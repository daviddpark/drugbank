(defproject drugbank "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :jvm-opts ["-Xms512m" "-Xmx1g"] 
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[api-lib "0.1.8-SNAPSHOT"]
                 [bigml/closchema "0.4"]
                 [cheshire "5.0.2"]
                 [clojurewerkz/elastisch "1.0.2"]
                 [compojure "1.1.5"]
                 [com.novemberain/langohr "1.4.1"]
                 [com.novemberain/monger "1.6.0"]
                 [environ "0.4.0"]
                 [liberator "0.9.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ring/ring-json "0.2.0"]]
  :eval-in-leiningen true
  :repositories ^:replace [["ocin-api" "http://maven.lillycoi.com/nexus/content/repositories/ocin-api/"]]
  :ring {:handler drugbank.handler/app})
