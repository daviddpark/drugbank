(defproject drugbank "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :jvm-opts ["-Xms512m" "-Xmx1g"] 
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[api-lib "0.1.8-SNAPSHOT"]
                 [bigml/closchema "0.4"]
                 [cheshire "5.0.2"]
                 [clojure-opennlp "0.3.1"]
                 [clojurewerkz/elastisch "1.0.2"]
                 [compojure "1.1.5"]
                 [com.novemberain/langohr "1.4.1"]
                 [com.novemberain/monger "1.6.0"]
                 [environ "0.4.0"]
                 [lein-ring "0.8.6"]
                 [liberator "0.9.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ring/ring-json "0.2.0"]]
  :repositories ^:replace [["ocin-api" "http://maven.lillycoi.com/nexus/content/repositories/ocin-api/"]]
  :ring {:handler drugbank.handler/app}
  :aliases {"bootstrap" ["trampoline" "run" "-m" "drugbank.tasks.bootstrap/bootstrap"]
            "process-indications" ["trampoline" "run" "-m" "drugbank.tasks.process-indications/process-indications"]
            "process-xml" ["trampoline" "run" "-m" "drugbank.tasks.process-xml/process-xml"]
            "reset" ["trampoline" "run" "-m" "drugbank.tasks.reset/reset"]
            "show-memory" ["trampoline" "run" "-m" "drugbank.tasks.show-memory/show-memory"]})
