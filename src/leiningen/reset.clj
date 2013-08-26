(ns leiningen.reset
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.java.io :as io]
   [clojurewerkz.elastisch.rest :as esr]   
   [clojurewerkz.elastisch.rest.index :as esi]   
   [monger.core :as mg]
   [monger.collection :as mc])
  (:use [drugbank.util :only [config]]
        [clojure.tools.logging :only [error info warn]]
        [environ.core]))

(defn drop-mongo-collection
  "Drop the mongo collection"
  [col-key]
  (let [collection (get-in config [:mongo col-key])]
    ))

(defn reset-mongo []
  (warn "...dropping collections from MongoDB")
  (let [servers (clojure.string/split (:servers (:mongo config)) #",")
        ports (map #(Integer/parseInt %) (clojure.string/split (:ports (:mongo config)) #","))
        seeds (vec (map vector servers ports))
        sas (map #(apply mg/server-address %) seeds)
        ]
    (mg/connect! sas (mg/mongo-options))
    (mg/set-db! (mg/get-db (get-in config [:mongo :dbname])))
    (let [mcs (:collections (:mongo config))]
      (doseq [coll-key (keys mcs)]
        (let [coll-name (:name (coll-key mcs))]
          (warn (str "Dropping collection " coll-name))
          (mc/drop coll-name))))))

(defn delete-index
  [index-keyword]
  (let [index-name (name index-keyword)]
    (warn (str "...deleting " index-name " index"))
    (try
      (esi/delete index-name)
      (catch Exception e (error e (str "Problem deleting index " index-name))))))

(defn reset-es []
  (warn "...deleting ElasticSearch indexes")
  (try
    (esr/connect! (:url (:elasticsearch config)))
    (doseq [k (keys (:settings (:elasticsearch config)))]
      (delete-index k))
    (catch java.net.ConnectException e (error e "Problem deleting indexes"))))

(defn reset-prov []
  (warn "...clearing prov data graph")
  (let [req {:content-type "text/turtle" :body ""}]
    (http/put (:data-url (:prov config)) req)))

(defn reset
  [project & args]
  (warn "Resetting project.")
  (reset-mongo)
  (reset-es))