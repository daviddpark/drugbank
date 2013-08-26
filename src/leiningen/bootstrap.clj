(ns leiningen.bootstrap
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojurewerkz.elastisch.rest :as esr]   
   [clojurewerkz.elastisch.rest.index :as esi]   
   [monger.core :as mg]
   [monger.collection :as mc])
  (:use [drugbank.util :only [config]]
        [clojure.tools.logging :only [error info warn]]
        [environ.core]))

(defn bootstrap-mongo []
  (info "...bootstrapping MongoDB")
  (let [servers (clojure.string/split (:servers (:mongo config)) #",")
        ports (map #(Integer/parseInt %) (clojure.string/split (:ports (:mongo config)) #","))
        seeds (vec (map vector servers ports))
        sas (map #(apply mg/server-address %) seeds)
        ]
    ; (mg/connect! {:host (first servers) :port (first ports)})
    (mg/connect! sas (mg/mongo-options))
    (mg/set-db! (mg/get-db (get-in config [:mongo :dbname])))
                                        ; This is a good place to create necessary mongo indexes.
    (let [mcs (:collections (:mongo config))]
      (doseq [coll-key (keys mcs)]
        (let [coll-config (coll-key mcs)
              coll-name (:name coll-config)]
          (info (str "......creating indexes for collection " coll-name))
          (doseq [idx-key (keys (:indexes coll-config))]
            (let [idx-config (idx-key (:indexes coll-config))
                  index (:index idx-config)
                  name-map {:name (name idx-key)}]
              (info (str ".........creating index " (name idx-key)))
              (if-let [extra (:extra idx-config)]
                (mc/ensure-index coll-name (apply array-map index) (merge extra name-map))
                (mc/ensure-index coll-name (apply array-map index) name-map)))))))))

(defn create-index-with-mappings
  [index-keyword]
  (let [index-name (name index-keyword)]
    (info (str "...creating " index-name " index"))
    (let [mappings (json/decode (slurp (io/resource (str index-name "-mappings.json"))))
          settings (get-in config [:elasticsearch :settings index-keyword])]
      (try
        (esi/create index-name :settings settings :mappings mappings)
        (catch Exception e (error e (str "Problem creating index " index-name))))))) 

(defn bootstrap-es []
  (info "...bootstrapping ElasticSearch")
      (try
        (esr/connect! (:url (:elasticsearch config)))
        (doseq [k (keys (:settings (:elasticsearch config)))]
          (create-index-with-mappings k))
        (catch java.net.ConnectException e (warn "Ignoring Elastic Search connect exception. "
                                                  "If not testing, then something is wrong. "
                                                  "Please check configuration in config.ini."))))
        ;(esi/delete (:google-loc-idx (:elasticsearch config)))

(defn bootstrap
  [project & args]
  (info "Bootstrapping project.")
  (bootstrap-mongo)
  (bootstrap-es)
  )