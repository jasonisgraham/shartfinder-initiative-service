(defproject shartfinder-initiative-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/carmine "2.9.0"]
                 [org.clojure/data.json "0.2.5"]
                 [http-kit "2.1.16"]
                 [compojure "1.3.1"]]
  :main ^:skip-aot shartfinder-initiative-service.core
  :uberjar-name "shartfinder-initiative-service-standalone.jar"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
