(ns drugbank.util
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojurewerkz.elastisch.rest.index :as esi]
            [closchema.core :as schema]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.exchange :as le]
            [langohr.queue :as lq]
            [langohr.basic :as lb])
  (:use [api-lib.util :only [move-id-to-links-self parse-validation-errors send-prov-message uri-prefix]]
        [cheshire.core :only [generate-string]]
        [clojure-ini.core :only [read-ini]]
        [clojure.core.incubator :only [dissoc-in]]
        [clojure.string :only [join]]
        [clojure.tools.logging :only [debug error info]]
        [clojure.walk :only [keywordize-keys]]
        [slingshot.slingshot :only [throw+ try+]]))

(def config (let [app-config (json/decode (slurp (io/resource "app_config.json")) true)
                  env-config (json/decode (slurp (io/resource "env_config.json")) true)]
              (merge-with merge app-config env-config)))

(defn term-map [m key-path]
  "Construct an elastic search term map only if there is a truthy value in map m traversing key-path"
  (if-let [value (get-in m key-path)]
    (let [key (join "." (map name key-path))]
      {:term {key value}})))

(defn drug-terms [drug]
  "Construct a vector containing relevant term maps. Null values for address components will not be included."
  (apply conj []
         (filter #(not (nil? %)) (map (partial term-map drug)
                                      [[:name]
                                       [:description]
                                       [:drugbank-idx]
                                       [:indication]
                                       [:brands :brand]]))))


;; ### extract-resource-result
;;
;; Given a search result from hitting elastic search,
;; return to inner source and the score
;;
(defn extract-resource-result [result]
  (if-let [url (get-in result ["_source" "_links" "self" "href"])]
    (assoc-in {:_links {:self {:href url}}}
            [:_meta :score] (get result "_score"))))

(defn keywordize-es-query [context]
  (if-let [result (keywordize-keys (if-let [query (get-in context [:request :query-params "query"])]
                     (json/parse-string query)
                     (get-in context [:request :json-params])))]
    result
    {:query {:bool {:must [{:match_all {}}]}}}))

(defn search-matching-drug [drug]
  "Given any attributes in drug, see if there is an exact match."
  (let [refresh-url (str (:url (:elasticsearch config)) (:ctgov-drug-idx (:elasticsearch config)) "/_refresh")]
    (http/post refresh-url))
  (let [must-terms (drug-terms drug)
        params {:query {:bool {:must must-terms}}}
        search-url (str (:url (:elasticsearch config))
                        (:ctgov-drug-idx (:elasticsearch config)) "/"
                        (:ctgov-drug-type (:elasticsearch config)) "/_search")
        query-params (json/generate-string params)
        resp (http/post search-url
                        {:body query-params
                         :content-type :json
                         :socket-timeout 1000  ;; in milliseconds
                         :conn-timeout 1000    ;; in milliseconds
                         :accept :json})
        body (json/parse-string (:body resp))
        hits (get body "hits")
        results (get hits "hits")
        drug (extract-resource-result (first results))]
    ;TODO: Score threshold here?
    (debug ":::::::::::::::::::::: SEARCH DRUG CONFLICT QUERY::::::::::::::::::::::::::")
    (debug query-params)
    (debug ":::::::::::::::::::::: CONFLICTING DRUG RESULT ::::::::::::::::::::::::::::")
    (debug drug)
    drug))

(defn search-drugs [context page size]
  (let [refresh-url (str (:url (:elasticsearch config)) (:ctgov-drug-idx (:elasticsearch config)) "/_refresh")]
      (http/post refresh-url))
  (let [page-size (Integer/parseInt size)
        start-from (* page-size (dec (Integer/parseInt page)))
        params (keywordize-es-query context)
        search-url (str (:url (:elasticsearch config))
                        (:ctgov-drug-idx (:elasticsearch config)) "/"
                        (:ctgov-drug-type (:elasticsearch config)) "/_search")
        query-params (json/generate-string (merge {:from start-from
                                                   :size page-size}
                                                  params))
        resp (http/post search-url
                        {:body query-params
                         :content-type :json
                         :socket-timeout 1000  ;; in milliseconds
                         :conn-timeout 1000    ;; in milliseconds
                         :accept :json})
        body (json/parse-string (:body resp))
        hits (get body "hits")
        results (get hits "hits")
        drugs (map extract-resource-result results)]
    (debug "::::::::::::::::::::::::::ELASTIC SEARCH QUERY:::::::::::::::::::::::::::::::")
    (debug query-params)
    (debug "::::::::::::::::::::::::::ELASTIC SEARCH RESULTS:::::::::::::::::::::::::::::::")
    (debug results)
    (debug "::::::::::::::::::::::::::DRUG RESULTS:::::::::::::::::::::::::::::::")
    (debug drugs)
    

    {:total (get hits "total")
     :results drugs}))


(defn drug-by-alias [context alias]
  (debug "(drug-by-alias " alias ")")
  ;TODO Order query by last injected descending!
  (let [search-url (str (:url (:elasticsearch config))
                        (:ctgov-drug-idx (:elasticsearch config)) "/"
                        (:ctgov-drug-type (:elasticsearch config)) "/_search")
        query {:query {:bool {:must [{:term {:drugbank-id alias}}]}}}
        query-params (json/generate-string
                      (merge {:from 0 :size 20} query))
        resp (http/post search-url
                        {:body query-params
                         :content-type :json
                         :socket-timeout 1000  ;; in milliseconds
                         :conn-timeout 1000    ;; in milliseconds
                         :accept :json})
        body (json/parse-string (:body resp))
        hits (get body "hits")
        results (get hits "hits")]
    (debug (str "Elastic Search Query: " query-params))
    (debug (str "elastic search drug by alias result: " (first results)))
    (if-let [result (first results)]
      (extract-resource-result result))))