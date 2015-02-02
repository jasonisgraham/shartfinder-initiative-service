(defproject shartfinder-initiative-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/carmine "2.9.0"]
                 [org.clojure/data.json "0.2.5"]
                 [http-kit "2.1.16"]
                 [compojure "1.3.1"]]
  :main shartfinder-initiative-service.web
  :uberjar-name "shartfinder-initiative-service-standalone.jar"
  :aot [shartfinder-initiative-service.web])

;; web: java $JVM_OPTS -cp target/shartfinder-initiative-service-standalone.jar clojure.main -m shartfinder-initiative-service.core
