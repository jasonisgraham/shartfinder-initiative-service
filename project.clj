(defproject shartfinder-initiative-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/carmine "2.9.0"]
                 [http-kit "2.1.16"]
                 [compojure "1.3.1"]
                 [clj-http "1.0.1"]
                 [sonian/carica "1.1.0"]
                 [cheshire "5.4.0"]]

  :classpath-add ["config/config.edn"]

  :main ^{:skip-aot true} shartfinder-initiative-service.web

  :uberjar-name "shartfinder-initiative-service-standalone.jar"

  :profiles {:uberjar {:aot :all}}

  :auto-clean false)
