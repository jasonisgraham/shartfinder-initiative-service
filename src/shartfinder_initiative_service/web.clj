(ns shartfinder-initiative-service.web
  (:require [shartfinder-initiative-service.core :refer :all]
            [org.httpkit.server :refer (run-server)]
            [compojure.core :refer (defroutes GET)])
  (:gen-class))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defroutes app
  (GET "/" [] (get-response)))

(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "5000"))]
    (prn "port: " port)
    (reset! server (run-server #'app {:port port}))))
