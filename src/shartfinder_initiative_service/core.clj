(ns shartfinder-initiative-service.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.set :as set]
            [shartfinder-initiative-service.utils :as initiative-utils]
            [clj-http.client :as client]
            [carica.core :refer [config]])
  (:gen-class))

(def server-connection {:pool {}
                        :spec (config :redis :spec)})

(def service-urls (config :service-urls))

(def channels {:encounter-created "encounter-created"

               :combatant-added "combatant-added"
               :add-combatant-command "add-combatant-command"

               :initiative-rolled "initiative-rolled"
               :roll-initiative-command "roll-initiative-command"
               :initiative-created "initiative-created"

               :error "error"})

(defonce combatants-rolled (atom {}))
(defonce combatants-received (atom {}))
(defonce ordered-initiative (atom []))
(defonce encounter-id (atom nil))

(defmacro wcar* [& body]
  `(car/wcar server-connection ~@body))

(defmacro handle-pubsub-subscribe [handle-event-fn]
  `(fn f1 [[type# match# content-json# :as payload#]]
     (when (instance? String content-json#)
       (let [content# (parse-string content-json# true)]
         (println "payload: " payload#)
         (~handle-event-fn content#)))))

(defn get-initiative-bonus [combatant-name]
  (let [url (str (service-urls :combatant) "/initiative-bonus/" combatant-name)
        response (client/get url {:throw-exceptions false})]
    (if (= (:status response) 200)
      (->> :initiativeBonus ((parse-string (:body response) true)) (Integer/parseInt))
      (wcar* (car/publish (:error channels)
                          (generate-string {:response (:status response)
                                            :url url
                                            :source "initiative-service"}))))))

(defn get-initiative-value [combatant-info]
  (let [initiative-bonus (get-initiative-bonus combatant-info)]
    (+ (initiative-utils/to-number (:diceRoll combatant-info)) initiative-bonus)))

(defn create-initiative [unordered-combatants]
  (let [combatants (keys unordered-combatants)
        initiatives (map #(:initiative %) (vals unordered-combatants))
        m (into (sorted-map) (initiative-utils/zippy initiatives combatants))
        ordered-combatants (-> m reverse vals flatten) ]
    (into {} (map #(assoc {} % (unordered-combatants %)) ordered-combatants))))

(defn is-combatant-in-combatants-received? [combatant-info]
  (not (nil? (@combatants-received (:combatantName combatant-info)))))

(defn has-combatant-rolled? [combatant-info]
  (not (nil? (@combatants-rolled (:combatantName combatant-info)))))

(defn should-allow-combatant-roll? [combatant]
  "returns true if combatant-name is in combatants-received & not in combatants-rolled"
  (println "is-combatant-in-combatants-received?: " (is-combatant-in-combatants-received? combatant))
  (println "has-combatant-rolled?" (has-combatant-rolled? combatant))
  (and (is-combatant-in-combatants-received? combatant)
       (not (has-combatant-rolled? combatant))))

(defn process-single-initiative [combatant-info]
  "If combatant roll is accepted, update @combatants-rolled"
  (println "process-single-initiative entered")
  (let [combatant-name (:combatantName combatant-info)]
    (println "inside 'let'")
    (when (should-allow-combatant-roll? combatant-info)
      (println "inside should-allow-combatant")
      (let [initiative-value (get-initiative-value combatant-info)
            combatant-info (assoc combatant-info :initiative initiative-value)]
        (swap! combatants-rolled assoc (:combatantName combatant-info) combatant-info)
        (println "combatants-rolled: " @combatants-rolled)
        (wcar* (car/publish (:initiative-rolled channels)
                            (generate-string combatant-info)))
        combatant-info))))

(defn who-hasnt-rolled? []
  (set/difference (set (keys @combatants-received))
                  (set (keys @combatants-rolled))))

(defn- has-everyone-rolled? []
  (>= (count @combatants-rolled)
      (count @combatants-received)))

(defn process-initiative-created [combatant-info]
  "process initiative roll, then if everyone has rolled, order initiative then publish"
  (process-single-initiative (update-in combatant-info [:diceRoll] initiative-utils/to-number))
  (when (has-everyone-rolled?)
    (let [ordered-combatants (create-initiative @combatants-rolled)]
      (reset! ordered-initiative {:encounterId @encounter-id, :orderedCombatants ordered-combatants})
      (let [publish-str (generate-string @ordered-initiative)]
        (wcar* (car/publish (channels :initiative-created) publish-str))
        publish-str))))

(defn initialize-received-combatants [combatants-info]
  (reset! encounter-id (combatants-info :encounterId))
  (reset! combatants-rolled {})
  (reset! ordered-initiative [])
  (reset! combatants-received (into {} (map #(assoc {} (:combatantName %) %) (:combatants combatants-info)))))

(defonce listener
  (car/with-new-pubsub-listener (:spec server-connection)
    {(:encounter-created channels) (handle-pubsub-subscribe initialize-received-combatants)
     (:roll-initiative-command channels) (handle-pubsub-subscribe process-initiative-created)}
    (car/subscribe (:encounter-created channels)
                   (:roll-initiative-command channels))))

(defn get-response []
  (generate-string {:combatants-received @combatants-received
                    :combatants-rolled @combatants-rolled
                    :ordered-initiative @ordered-initiative
                    :who-hasnt-rolled (who-hasnt-rolled?)}))
