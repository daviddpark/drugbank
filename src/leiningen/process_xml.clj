(ns leiningen.process-xml
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.java.io :as io])
  (:use
   [clojure.data.xml]
   [drugbank.util :only [config]]))

(defn process-element [elem]
  (let [content (:content elem)
        is-multiple (and (> (count content) 1)
                         (apply = (map :tag content)))
        has-attrs (> (count (:attrs elem)) 0)]
    (let [result {(:tag elem)
                  (if (and (= (str (type (first content))) "class java.lang.String")
                           (not has-attrs) (not is-multiple))
                    (first content)
                    (if is-multiple
                      (apply conj [] (map process-element content))
                      (if (not (empty? content))
                        (apply merge (map process-element content))))
                    )}]
      (if has-attrs
        (merge {:attributes (:attrs elem)}
               result)
        result))))

(defn process-drug [elem]
  (println "process-drug")
  (let [drug (merge {:attributes (:attrs elem)}
                    (apply merge
                           (map process-element (:content elem))))
        json-drug (json/generate-string drug 
                                        {:pretty true})]
    (println "Finished processing drug " (:drugbank-id drug))
    (let [req {:body json-drug
                   :content-type :json
                   :accept :json
                   :throw-exceptions false}
          resp (http/post (str (get-in config [:api :url]) "drug") req)]
      (if (= (:status resp) 409)
        (let [current-resp (http/get (:body resp) {:content-type :json :accept :json})
              current (json/parse-string (:body current-resp))
              current-version (get current "version")
              current-drug (json/generate-string (assoc drug :version current-version) {:pretty true})
              resp (http/put (:body resp) (assoc req :body current-drug))]
          (println "Conflict response: " resp))))))

(defn process-partners [elem]
  ;(println "process-partners")
  ;(println (keys elem))
  )

(defn process-partner [elem]
  ;(println "process-partner")
  )

(defn process-xml [project & args]
  (let [drugs-file (if (empty? args)
                     "/tmp/drugbank.xml"
                     (first args))]
    (if (empty? args)
      (let [zip-file "/tmp/drugbank.xml.zip"
            data-url "http://www.drugbank.ca/system/downloads/current/drugbank.xml.zip"
            con (-> data-url java.net.URL. .openConnection)
            buffer (make-array Byte/TYPE 4096)
            in (java.util.zip.ZipInputStream. (.getInputStream con))]
        (loop [ze (.getNextEntry in)
               ze-size (.getSize ze)]
          (if-not (nil? ze)
            (let [out (java.io.FileOutputStream. drugs-file)]
              (loop [g (.read in buffer 0 4096)
                     r 0]
                (if-not (= g -1)
                  (do (println r "/" ze-size)
                      (.write out buffer 0 g)
                      (recur (.read in buffer 0 4096) (+ r g)))))
              (.close out))
            ))
        (.close in)
        (.disconnect con)))
    (def data (parse (io/reader drugs-file)))
    (loop [elems (:content data)]
      (let [elem (first elems)]
        (if (= (:tag elem) :drug)
          (process-drug elem)
          (if (= (:tag elem) :partners)
            (process-partners elem))))
      (if (next elems)
        (recur (rest elems))))))