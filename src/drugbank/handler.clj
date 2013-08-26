(ns drugbank.handler
  (:use [api-lib.middleware :only [keywordize-json-params]]
        ;[clj-logging-config.log4j]
        [clojure.tools.logging :only [debug error info warn]]
        [drugbank.resources]
        [drugbank.util :only [config]]
        [compojure.core]
        [environ.core])
  (:require [cheshire.core :as json]
            [api-lib.wds]
            [clojure.java.io :as io]
            [compojure.handler :as handler]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [compojure.route :as route]
            [liberator.dev :as dev];;TODO -remove for production
            [monger.core :as mg]
            [ring.middleware.json :as middleware]))

;; ### initialize
;;
;; Initialize application, ensuring connections to RabbitMQ and
;; elasticsearch
;;
(defn initialize []
  (info "INITIALIZING APPLICATION")
  (let [servers (clojure.string/split (:servers (:mongo config)) #",")
        ports (map #(Integer/parseInt %) (clojure.string/split (:ports (:mongo config)) #","))
        seeds (vec (map vector servers ports))
        sas (map #(apply mg/server-address %) seeds)
        ]
    (if (nil? (env :testing))
      (do
        ;;(mg/connect! {:host (first servers) :port (first ports)})
        (mg/connect! sas (mg/mongo-options))
        (mg/set-db! (mg/get-db (get-in config [:mongo :dbname])))))
    (try
      (esr/connect! (:url (:elasticsearch config)))
      (catch java.net.ConnectException e (warn "Ignoring Elastic Search connect exception. "
                                                  "If not testing, then something is wrong. "
                                                  "Please check configuration in config.ini.")))))
;; ### app-routes
;;
(defroutes app-routes
  ;(ANY "/" [] root-resource)
  (ANY ["/drug/alias/:id" :id #".+"]  [id] (drug-instance-resource-by-alias id))
  (ANY ["/drug/:id" :id #".+"]  [id] (drug-instance-resource id))
  (ANY "/drug" [] drug-resource)
  (ANY "/drugs" [] drugs-resource)
  (ANY "/drugs/search" [] drugs-search-resource)
  (route/not-found "Not Found"))

(def app
  (->   (handler/site app-routes)
        (middleware/wrap-json-body)
        (keywordize-json-params)
        (middleware/wrap-json-params)
        (middleware/wrap-json-response {:pretty true})
        ;(dev/wrap-trace :header :ui)
        ))

(initialize)