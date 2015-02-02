(ns shartfinder-initiative-service.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [shartfinder-initiative-service.combatant-service :as combatant-service]
            [shartfinder-initiative-service.utils :as initiative-utils]
            [org.httpkit.server :refer (run-server)]
            [compojure.core :refer (defroutes GET)])
  (:gen-class))

(def server-connection {:pool {}
                        :spec {:host "pub-redis-18240.us-east-1-3.1.ec2.garantiadata.com"
                               :port 18240
                               :password "abc123"}})

(def ^:private channels {:encounter-created "encounter-created"
                         :initiative-rolled "roll-initiative"
                         :initiative-created "initiative-created"})

(def ^:private identifiers {:combatant-id "playerId"
                            :dice-roll "diceRoll"})

(defmacro wcar* [& body] `(car/wcar server-connection ~@body))

;; I'm using atoms b/c I don't know clojure very well don't know no better :(
(defonce combatants-rolled (atom {}))
(defonce combatants-received (atom #{}))
(defonce server (atom nil))
(defonce ordered-initiative (atom nil))

(defn get-combatants-from-json [content-json]
  "accepts gmCombatants, playerCombatants, combatants"
  (concat
   (content-json "gmCombatants")
   (content-json "playerCombatants")
   (content-json "combatants")))

(defn get-initiative [combatant-id dice-roll]
  (+ dice-roll (combatant-service/get-initiative-bonus combatant-id)))

(defn create-initiative [unordered-initiative-map]
  (let [combatants (keys unordered-initiative-map)
        initiatives (vals unordered-initiative-map)
        m (into (sorted-map) (initiative-utils/zippy initiatives combatants))
        ordered-combatants (reverse m)]
    (flatten (vals ordered-combatants))))

(defn should-allow-combatant-roll? [combatant-id]
  "returns true if combatant-id is in combatants-received & not in combatants-rolled"
  (and (some #{combatant-id} @combatants-received)
       (nil? (@combatants-rolled combatant-id))))

(defn process-single-initiative [initiative-json-string]
  "Accepts json String with EncounterId, CombatantId, & DiceValue"
  (let [initiative-json (json/read-str initiative-json-string)
        combatant-id (initiative-json (identifiers :combatant-id))]
    (when (should-allow-combatant-roll? combatant-id)
      (let [initiative (get-initiative combatant-id (initiative-json (identifiers :dice-roll)))]
        (swap! combatants-rolled assoc combatant-id initiative)))))

(defn who-hasnt-rolled? []
  (set/difference @combatants-received (keys @combatants-rolled)))

(defn- has-everyone-rolled? []
  (>= (count @combatants-rolled) (count @combatants-received)))

(defn process-initiative-created [content-json]
  (process-single-initiative content-json)
  (when (has-everyone-rolled?)
    (let [ordered-initiative-json (create-initiative @combatants-rolled)]
      (reset! ordered-initiative ordered-initiative-json)
      (wcar* (car/publish (channels :initiative-created) ordered-initiative-json)))))

(defn initialize-received-combatants [combatant-json]
  (->> combatant-json (json/read-str) (get-combatants-from-json) (set) (reset! combatants-received)))

(defonce listener
  (car/with-new-pubsub-listener (:spec server-connection)
    {(channels :encounter-created) (fn f1 [[type match content-json :as payload]]
                                     (when (instance? String content-json)
                                       (initialize-received-combatants content-json)))
     (channels :initiative-rolled) (fn f2 [[type match content-json :as payload]]
                                     (when (instance? String content-json)
                                       (process-initiative-created content-json)))}
    (car/subscribe (channels :encounter-created) (channels :initiative-rolled))))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defroutes app
  (GET "/" [] (json/write-str {:who-hasnt-rolled (who-hasnt-rolled?)
                               :combatants-received @combatants-received
                               :combatants-rolled @combatants-rolled
                               :ordered-initiative @ordered-initiative})))

(defn -main [& args]
  (reset! server (run-server #'app {:port 5000})))
