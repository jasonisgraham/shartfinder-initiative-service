(ns shartfinder-initiative-service.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [shartfinder-initiative-service.utils :as initiative-utils]
            [clj-http.client :as client])
  (:gen-class))

(def server-connection {:pool {}
                        :spec {:host "pub-redis-18240.us-east-1-3.1.ec2.garantiadata.com"
                               :port 18240
                               :password "abc123"}})

(def ^:private service-urls {:combatant "https://secure-beach-3319.herokuapp.com/"})

(def ^:private channels {:encounter-created "encounter-created"
                         :initiative-rolled "roll-initiative"
                         :initiative-created "initiative-created"
                         ;; TODO should this be an error only API gateway listens to??
                         :error "error"})

(def ^:private identifiers {:combatant-id :playerId
                            :dice-roll :diceRoll
                            :initiative-bonus :initiativeBonus})

(defmacro wcar* [& body]
  `(car/wcar server-connection ~@body))

;; I need to add something that allows these to be reset if nessy, or (better yet), just include encounter-id
(defonce combatants-rolled (atom {}))
(defonce combatants-received (atom #{}))
(defonce ordered-initiative (atom nil))
(defonce encounter-id (atom nil))

(defn get-combatants-from-json [ content]
  "accepts gmCombatants, playerCombatants, combatants"
  (concat (content :gmCombatants) (content :playerCombatants) (content :combatants)))

(defn get-initiative-bonus [combatant-id]
  (let [url (str (service-urls :combatant) "/initiative-bonus/id")
        response (client/get url {:throw-exceptions false})]
    (if (= (:status response) 200)
      (->> (:initiative-bonus identifiers) ((json/read-str (:body response) :key-fn keyword)) (Integer/parseInt))
      ;; TODO probably better way to produce this
      (wcar* (car/publish (channels :error) (json/write-str {:response (:status response)
                                                             :url url
                                                             :source "initiative-service"}))))))

(defn get-initiative [combatant-id dice-roll]
  (let [initiative-bonus (get-initiative-bonus combatant-id)]
    (+ dice-roll initiative-bonus)))

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

(defn process-single-initiative [initiative]
  "Accepts json String with EncounterId, CombatantId, & DiceValue"
  (let [combatant-id ((identifiers :combatant-id) initiative)]
    (when (should-allow-combatant-roll? combatant-id)
      (let [initiative (get-initiative combatant-id (initiative (identifiers :dice-roll)))]
        (swap! combatants-rolled assoc combatant-id initiative)))))

(defn who-hasnt-rolled? []
  (set/difference @combatants-received (keys @combatants-rolled)))

(defn- has-everyone-rolled? []
  (>= (count @combatants-rolled) (count @combatants-received)))

(defn process-initiative-created [ content-json]
  (process-single-initiative  content-json)

  (when (has-everyone-rolled?)
    (let [ordered-combatant-ids (create-initiative @combatants-rolled)]
      (reset! ordered-initiative {:encounterId @encounter-id, :orderedCombatantIds ordered-combatant-ids})
      ;; returns the json payload (this will help with testing)
      ;; I'm hoping there is a better way to test this redis stuff
      (let [publish-str (json/write-str @ordered-initiative)]
        (wcar* (car/publish (channels :initiative-created) publish-str))
        publish-str))))

(defn initialize-encounter-id [combatant-json]
  (reset! encounter-id (-> combatant-json (get :encounterId))))

(defn initialize-received-combatants [combatant-json]
  (initialize-encounter-id combatant-json)
  (->> combatant-json  (get-combatants-from-json) (set) (reset! combatants-received)))

(defonce listener
  (car/with-new-pubsub-listener (:spec server-connection)
    {(channels :encounter-created) (fn f1 [[type match  content-json :as payload]]
                                     (when (instance? String  content-json)
                                       (initialize-received-combatants  content-json)))
     (channels :initiative-rolled) (fn f2 [[type match  content-json :as payload]]
                                     (when (instance? String  content-json)
                                       (process-initiative-created (json/read-str content-json))))}
    (car/subscribe (channels :encounter-created) (channels :initiative-rolled))))

(defn get-response []
  (json/write-str {:combatants-received @combatants-received
                   :combatants-rolled @combatants-rolled
                   :ordered-initiative @ordered-initiative
                   :who-hasnt-rolled (who-hasnt-rolled?)}))
