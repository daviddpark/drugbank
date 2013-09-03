(ns drugbank.resources
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clj-http.client :as http])
  (:use [api-lib.util :only [parse-validation-errors uri-prefix collection-prefix build-collection]]
        [clojure.core.incubator :only [dissoc-in]]
        [clojure.string :only [lower-case]]
        [clojure.tools.logging :only [debug error info]]
        [clojure.walk :only [keywordize-keys]]
        [hiccup.page :only [html5]]
        [liberator.core :only [defresource request-method-in with-console-logger gen-etag]]
        [liberator.representation :only [ring-response]]
        [drugbank.persistence]
        [drugbank.util]))

(defn construct-drug-uri [context id]
  (str (uri-prefix context "drug") id))

(defresource drug-resource
  :available-media-types ["application/json" "application/wds+json"]
  :handle-created (fn [context]
                    (if-let [conflict-msg (:conflict-msg context)]
                      (ring-response {:status 409 :body conflict-msg :headers {"Content-Type" "text/plain"}})
                      (:orig-resource context)))
  :handle-malformed (fn [context] (:validation-error context))
  ;:malformed? drug-malformed?
  :method-allowed? #(some #{(get-in % [:request :request-method])} [:options :post])
  :post! (fn [context]
           (let [informant (get-in context [:request :headers "informant"])
                 agent (get-in context [:request :headers "provagent"])
                 drug (get-in context [:request :json-params])]
             (if-let [orig-drug (drug-by-alias context (:drugbank-id drug))]
               (do (debug (str "Conflict detected, but be sure to check the score!" orig-drug))
                   {:conflict-msg (get-in orig-drug [:_links :self :href])})
               (let [new-drug (insert-drug (uri-prefix context "drug") drug agent informant)]
                 {:orig-resource new-drug})))))

(defresource drug-instance-resource-by-alias [id]
  :available-media-types ["application/json" "application/wds+json"]
  :exists? (fn [context]
             (if-let [url (get-in (drug-by-alias context id) [:_links :self :href])]
               [true {:orig-resource (get-drug url)}]
               false))
  :handle-ok (fn [context] (:orig-resource context))
  :method-allowed? #(some #{(get-in % [:request :request-method])} [:get]))


(defresource drug-instance-resource [id]
  :available-media-types ["application/json" "application/wds+json"];"text/html" "text/plain"
  :can-put-to-missing? false
  :conflict? (fn [context]
               (let [current-version (get-in context [:orig-resource :version])
                     payload-version (get-in context [:request :json-params :version])]
                 (not= current-version payload-version)))
  :delete! (fn [context]
             ;;TODO: If we want to support 410 Gone, we should modify
             ;;this function as well as :exists?
             (let [informant (get-in context [:request :headers "informant"])
                   agent (get-in context [:request :headers "provagent"])]
               (remove-drug (construct-drug-uri context id) agent informant)))
  :exists? (fn [context]
             (if-let [drug (get-drug (construct-drug-uri context id))]
               [true {:orig-resource drug}]
               false))
  :handle-conflict (fn [context]
                     (str "You are attempting to modify a drug resource that has recently been modified. "
                          "Please retrieve the latest version of the drug resource at "
                          (construct-drug-uri context id)
                          " and make your changes to that and resubmit."))
  :handle-created (fn [context] (:orig-resource context))
  ;:handle-malformed (fn [context] (:validation-error context))
  :handle-ok (fn [context] (:orig-resource context))
  ;:malformed? drug-malformed?
  :method-allowed? #(some #{(get-in % [:request :request-method])} [:options :delete :get :put])
  :new? false
  :put! (fn [context]
          (let [uri (construct-drug-uri context id)
                drug (assoc-in (dissoc-in (get-in context [:request :json-params]) [:_links :site])
                              [:_links :self :href] uri)
                agent (get-in context [:request :headers "provagent"])
                informant (get-in context [:request :headers "informant"])
                updated-drug (update-drug uri drug agent informant)]
            {:orig-resource updated-drug}))
  :respond-with-entity? true)

(defn search-drugs-build-collection [context]
  (let [page (get-in context [:request :params :page] "1")
        size (get-in context [:request :params :size]  "10")
        search-results (search-drugs context page size)
        fields (get-in context [:request :params :fields])
        formatted-results (map #(merge (get-drug (get-in % [:_links :self :href]) fields)
                                       (dissoc % :_links))
                               (:results search-results))]
    (build-collection (:total search-results) (if (= "/drugs" (get-in context [:request :uri]))
                                                (map #(dissoc % :_meta) formatted-results)
                                                formatted-results)
                      (collection-prefix context "drugs") page size nil (keywordize-es-query context))))

(defresource drugs-search-resource
  :available-media-types ["application/json"]
  :method-allowed? #(some #{(get-in % [:request :request-method])} [:options :post :get])
  ;:malformed? drugs-search-params-malformed?
  :handle-malformed (fn [context]
                      (:validation-error context))
  :handle-created search-drugs-build-collection
  :handle-ok search-drugs-build-collection)

(defresource drugs-resource
  :available-media-types ["application/json"]
  :method-allowed? #(some #{(get-in % [:request :request-method])} [:options :get])
  :malformed? (fn [context]
               ;; If not paginated/offset/query params, return true
               false)
  :handle-ok (fn [context]
               (build-collection (count-drugs)
                                 (get-drugs (get-in context [:request :params]))
                                 (collection-prefix context "drugs")
                                 (get-in context [:request :params :page] "1")
                                 (get-in context [:request :params :size] "10")
                                 (get-in context [:request :params :fields])
                                 nil)))