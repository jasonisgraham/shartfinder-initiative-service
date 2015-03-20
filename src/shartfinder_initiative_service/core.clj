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
                         :initiative-rolled-success "roll-initiative-success"
                         :initiative-created "initiative-created"
                         ;; TODO should this be an error only API gateway listens to??
                         :error "error"})

(defmacro wcar* [& body]
  `(car/wcar server-connection ~@body))

(defonce combatants-rolled (atom {}))
(defonce combatants-received (atom {}))
(defonce ordered-initiative (atom []))
(defonce encounter-id (atom nil))

(defn get-initiative-bonus [combatant-name]
  (let [url (str (service-urls :combatant) "/initiative-bonus/combatant-name")
        response (client/get url {:throw-exceptions false})]
    (if (= (:status response) 200)
      (->> :initiativeBonus ((json/read-str (:body response) :key-fn keyword)) (Integer/parseInt))
      (wcar* (car/publish (channels :error) (json/write-str {:response (:status response)
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
  (and (is-combatant-in-combatants-received? combatant)
       (not (has-combatant-rolled? combatant))))

(defn process-single-initiative [combatant-info]
  "If combatant roll is accepted, update @combatants-rolled"
  (let [combatant-name (:combatantName combatant-info)]
    (println "combatant-name: " combatant-name)
    (println "should-allow-combatant-roll: " (should-allow-combatant-roll? combatant-info))
    (when (should-allow-combatant-roll? combatant-info)
      (let [initiative-value (get-initiative-value combatant-info)
            combatant-info (assoc combatant-info :initiative initiative-value)]
        (println "initiative-value: " initiative-value)
        (println "combatant-info: " combatant-info)
        (println "combatants-rolled: " combatants-rolled)
        (swap! combatants-rolled assoc (:combatantName combatant-info) combatant-info)
        (println "combatants-rolled: " combatants-rolled)
        (wcar* (car/publish (:initiative-rolled-success channels)
                            (json/write-str combatant-info)))
        combatant-info))))

(defn who-hasnt-rolled? []
  (set/difference (set (keys @combatants-received))
                  (set (keys @combatants-rolled))))

(defn- has-everyone-rolled? []
  (>= (count @combatants-rolled) (count @combatants-received)))

(defn process-initiative-created [combatant-info]
  "process initiative roll, then if everyone has rolled, order initiative then publish"
  (println "combatant-info: " combatant-info)
  (process-single-initiative (update-en combatant-info [:diceRoll] initiative-utils/to-number))
  (println "combatants: " @combatants-rolled)
  (when (has-everyone-rolled?)
    (let [ordered-combatants (create-initiative @combatants-rolled)]
      (reset! ordered-initiative {:encounterId @encounter-id, :orderedCombatants ordered-combatants})
      ;; returns the json payload (this will help with testing) I'm hoping there is a better way to test this redis stuff
      (let [publish-str (json/write-str @ordered-initiative)]
        (wcar* (car/publish (channels :initiative-created) publish-str))
        publish-str))))

(defn initialize-received-combatants [combatants-info]
  "TODO what's the better way to do this?"
  (reset! encounter-id (combatants-info :encounterId))
  (reset! combatants-received (into {} (map #(assoc {} (:combatantName %) %) (:combatants combatants-info)))))

(defonce listener
  (car/with-new-pubsub-listener (:spec server-connection)
    {(channels :encounter-created) (fn f1 [[type match  content-json :as payload]]
                                     (when (instance? String  content-json)
                                       (println "content-json: " content-json)
                                       (initialize-received-combatants (json/read-str content-json :key-fn keyword))))
     (channels :initiative-rolled) (fn f2 [[type match  content-json :as payload]]
                                     (println "init rolled")
                                     (println "content-json: " content-json)
                                     (when (instance? String  content-json)
                                       (process-initiative-created (json/read-str content-json :key-fn keyword))))}
    (car/subscribe (channels :encounter-created) (channels :initiative-rolled))))

(defn get-response []
  (println "here")
  (json/write-str {:combatants-received @combatants-received
                   :combatants-rolled @combatants-rolled
                   :ordered-initiative @ordered-initiative
                   :who-hasnt-rolled (who-hasnt-rolled?)}))
