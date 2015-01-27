(ns shartfinder-initiative-service.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.data.json :as json]
            [shartfinder-initiative-service.dice-roller :as dice-roller]
            [shartfinder-initiative-service.combatant-service :as combatant-service]
            [shartfinder-initiative-service.utils :as initiative-utils])
  (:gen-class))

(def server-connection {:pool {}
                        :spec {:host "pub-redis-18240.us-east-1-3.1.ec2.garantiadata.com"
                               :port 18240
                               :password "abc123"}})

(def channels {:encounter-created "encounter-created"
               :create-initiative "roll-initiative"
               :initiative-created "initiative-created"})

(defmacro wcar* [& body] `(car/wcar server-connection ~@body))

;; I'm using atoms b/c I don't know clojure very well don't know no better :(
(def characters-rolled (atom {}))
(def characters-received (atom []))

(defn get-players-from-json [content-json]
  "accepts gmCombatants, playerCombatants, combatants"
  (concat (content-json "gmCombatants") (content-json "playerCombatants") (content-json "combatants")))

(defn get-initiative [player-id dice-roll]
  "TODO get initiative-bonuses when :encounter-created event is picked up, then use that instead of getting everyone at the end"
  (+ dice-roll (combatant-service/get-initiative-bonus player-id)))

(defn create-initiative [json-string]
  )

(defn process-single-initiative [initiative-json-string]
  "Accepts json String with EncounterId, PlayerId, & DiceValue"
  (let [initiative-json (json/read-str initiative-json-string)
        player-id (initiative-json "playerId")
        initiative (get-initiative player-id (initiative-json "diceRoll"))]
    ;; FIXME im not actually verifying anything!
    (swap! characters-rolled assoc player-id initiative)))

(defn process-initiative-created [content-json]
  "FIXME not yet implemented"
  (if (<= (count @characters-received) (count @characters-rolled))
    (wcar@ (car/public (channels :initiative-created) order-json))))

(def listener
  (car/with-new-pubsub-listener (:spec server-connection)
    {(channels :encounter-created) (fn f1 [[type match content-json :as payload]]
                                     (when (instance? String content-json)
                                       (swap! characters-received (get-players-from-json (json/read-str content-json)))))
     (channels :create-initiative) (fn f2 [[type match content-json :as payload]]
                                      (process-initiative-created content-json))}
    (car/subscribe (channels :encounter-created))))


;; This function is balls ugly and when I learn clojure better, hoping to pretty it up
;; TODO this does not resolve collision with same dice num.  thinking of shuffling val set by keys 1-20
;; (defn create-initiative
;;   ([content-json]
;;    (let [players (get-players-from-json content-json)
;;          dice-outcomes (take (count players) (repeatedly #(dice-roller/roll-dice nil)))]
;;      (create-initiative content-json dice-outcomes)))
;;   ([content-json dice-outcomes]
;;    ;; the dice-outcomes option is only here b/c I dont know how to mock out functions when testing
;;    (let [players (get-players-from-json content-json)
;;          m (into (sorted-map) (initiative-utils/zippy dice-outcomes players))
;;          ordered-players (reverse m)]
;;      (flatten (vals ordered-players)))))

;; (defn- create-and-publish-initiative [content-json]
;;   (let [initiative-json (->> content-json
;;                              (json/read-str)
;;                              (create-initiative)
;;                              (hash-map :order)
;;                              (json/write-str))]
;;     (wcar* (car/publish (channels :initiative-created) initiative-json))))

(defn -main [])
