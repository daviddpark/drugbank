(ns drugbank.persistence
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [monger.collection :as collx]
            [monger.query :as mq]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clj-http.client :as http])
  (:use [api-lib.util :only [move-id-to-links-self send-prov-message]]
        [drugbank.util :only [config]]
        [clojure-ini.core :only [read-ini]]
        [clojure.tools.logging :only [debug error info]]
        [monger.operators])
  (:import [org.bson.types ObjectId]))

(def ^:const drug-col (get-in config [:mongo :collections :drug :name]))
(def ^:const drug-archive-col (get-in config [:mongo :collections :drug-archive :name]))
(def ^:const drug-type "drug")
(def ^:const drug-resource-agent "drug-resource")


(def amqp-settings {:host (:host (:amqp config))
                    :port (:port (:amqp config))
                    :username (:username (:amqp config))
                    :password (:password (:amqp config))})

(defn count-drugs [] (collx/count drug-col))

(defn increment-version [entity]
  (let [v (get entity :version 0)]
    (assoc (dissoc entity :version) :version (inc v))))

(defn get-drug [uri & [fields]]
  (info (str "(get-drug " uri ")"))
  (move-id-to-links-self
   (if (nil? fields)
     (collx/find-map-by-id drug-col uri)
     (collx/find-map-by-id drug-col uri (clojure.string/split fields #",")))))

(defn get-drugs [params]
  (try
    (map move-id-to-links-self
         (let [page (Integer/parseInt (get params :page "1"))
               size (Integer/parseInt (get params :size "10"))]
           (if-let [fields (:fields params)]
             (mq/with-collection drug-col (mq/find {})
               (mq/sort (sorted-map :fn 1))
               (mq/fields (clojure.string/split fields #","))
               (mq/paginate :page page :per-page size))
             (mq/with-collection drug-col (mq/find {})
               (mq/sort (sorted-map :fn 1))
               (mq/paginate :page page :per-page size)))))
    (catch Exception e {:error (str e)})))

;(insert-drug "https://drug.com/" {} "http://agent.com/1" "http://activity.com/1")
(defn insert-drug [uri-prefix drug informing-agent informing-activity]
  (let [uri (str uri-prefix (ObjectId.))
        lsh (get-in drug [:_links :self :href])
        new-drug (increment-version (assoc-in drug [:_links :self :href] uri))]
    (debug (str "(insert-drug " new-drug))
    (collx/insert drug-col (merge drug {:_id uri :version 1}))
    (let [es-result (esd/put (:drugbank-idx (:elasticsearch config))
             (:drug-type (:elasticsearch config))
             uri new-drug)]
      (debug "Elastic PUT result: " es-result))
    (send-prov-message {:uri uri
                        :entity-type drug-type
                        :activity-type "create"
                        :resource-agent drug-resource-agent
                        :informing-agent informing-agent
                        :informing-activity informing-activity
                        :amqp-settings amqp-settings})
    new-drug))

(defn remove-drug [uri informing-agent informing-activity]
  (let [orig-drug (get-drug uri)]
    (collx/insert drug-archive-col (assoc-in orig-drug [:_links :self :href] uri))
    (collx/remove-by-id drug-col uri)
    (esd/delete (:drugbank-idx (:elasticsearch config))
                (:drug-type (:elasticsearch config))
                uri)
    (send-prov-message {:uri uri
                        :entity-type drug-type
                        :activity-type "delete"
                        :resource-agent drug-resource-agent
                        :informing-agent informing-agent
                        :informing-activity informing-activity
                        :amqp-settings amqp-settings})))

(defn update-drug [uri drug informing-agent informing-activity]
  (let [orig-drug (get-drug uri)
        archive-id (ObjectId.)
        incremented-drug (increment-version drug)]
    (collx/insert drug-archive-col (assoc orig-drug :_id archive-id))
    (collx/update-by-id drug-col uri incremented-drug)
    (esd/put (:drugbank-idx (:elasticsearch config))
             (:drug-type (:elasticsearch config))
             uri incremented-drug)
    (send-prov-message {:uri uri
                        :entity-type drug-type
                        :activity-type "update"
                        :resource-agent drug-resource-agent
                        :informing-agent informing-agent
                        :informing-activity informing-activity
                        :amqp-settings amqp-settings})
    incremented-drug))

