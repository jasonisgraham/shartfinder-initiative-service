(ns shartfinder-initiative-service.web
  (:require [shartfinder-initiative-service.core :as initiative-service-core]
            [org.httpkit.server :refer (run-server)]
            [compojure.core :refer (defroutes GET)])
  (:gen-class))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defroutes app
  (GET "/" [] (json/write-str {:who-hasnt-rolled (initiative-service-core/who-hasnt-rolled?)
                               :combatants-received initiative-service-core/@combatants-received
                               :combatants-rolled initiative-service-core/@combatants-rolled
                               :ordered-initiative initiative-service-core/@ordered-initiative})))

(defn -main [& args]
  (reset! server (run-server #'app {:port 5000})))
