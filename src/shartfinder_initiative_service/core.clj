(ns shartfinder-initiative-service.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.data.json :as json]
            [shartfinder-initiative-service.dice-roller :as dice-roller])
  (:gen-class))

(def server-connection {:pool {}
                        :spec {:host "pub-redis-18240.us-east-1-3.1.ec2.garantiadata.com"
                               :port 18240
                               :password "abc123"}})

(def channels {:encounter-created "encounter-created"
               :initiative-created "initiative-created"})

(defmacro wcar* [& body] `(car/wcar server-connection ~@body))

(defn get-players-from-json [content-json]
  (concat (content-json "gmCombatants") (content-json "playerCombatants")))

;; {"id":6,"name":"Goblin encounter","gmCombatants":["goblin1","goblin2","ogre"],"playerCombatants":["tom","jason"]}
(defn create-initiative
  "This function is balls ugly and when I learn clojure better, hoping to pretty it up
   TODO this does not resolve collision with same dice num.  thinking of shuffling val set by keys 1-20"
  ([content-json]
   (let [players (get-players-from-json content-json)
         dice-outcomes (take (count players) (repeatedly #(dice-roller/roll-dice nil)))]
     (create-initiative content-json dice-outcomes)))
  ([content-json dice-outcomes]
   ;; the dice-outcomes option is only here b/c I dont know how to mock out functions when testing
   (let [players (get-players-from-json content-json)
         m (into (sorted-map) (zipmap dice-outcomes players))
         ordered-players (reverse m)]
     (vals ordered-players))))

(defn- create-and-publish-initiative [content-json]
  (let [initiative-json (->> content-json
                             (json/read-str)
                             (create-initiative)
                             (hash-map :order)
                             (json/write-str))]
    (wcar* (car/publish (channels :initiative-created) initiative-json))))

(def listener
  (car/with-new-pubsub-listener (:spec server-connection)
    {(channels :encounter-created) (fn f1 [[type match content-json :as payload]]
                                     (when (instance? String content-json)
                                       (create-and-publish-initiative content-json)))}
    (car/subscribe (channels :encounter-created))))
