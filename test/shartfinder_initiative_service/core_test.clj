(ns shartfinder-initiative-service.core-test
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [shartfinder-initiative-service.core :refer :all]))


;; (comment
;;   "efc4d149-41b9-4efd-8282-2b87146b3b21"
;;   "4cd438e4-b686-4f80-a4e9-9ef21c096e77"
;;   "d7411446-d1ba-45db-ab8f-16b3264c7dc0"
;;   d9d3bba1-d0f1-449e-8c7c-f038769deeca
;;   nil
;;   )

(comment
  The service responds with something like this
  {:encounterId 69, :orderedCombatants [{:userId "Jason", :combatantName "SilverNoun"}
                                        {:userId "Jim", :combatantName "Goblin 1"}
                                        {:userId "Dogman", :combatantName "Mean Mouth"}]}

  subscribes to "encounter-created" which looks like this
  {:encounterId 69, :combatants [ {:userId "jim", :combatantName "DTL Boss"}, {:userId "boolnerbroni", :combatantName "Grook"}] })

(defn create-test-combatant
  ([user-id combatant-name]
   {:userId user-id, :combatantName combatant-name})
  ([user-id combatant-name dice-roll]
   {:userId user-id, :combatantName combatant-name, :diceRoll dice-roll}))

(defn create-silver-noun
  ([]
   (create-test-combatant "jason" "SilverNoun"))
  ([dice-roll]
   (create-test-combatant "jason" "SilverNoun" dice-roll)))

(defn create-mean-mouth
  ([]
   (create-test-combatant "dogman" "MeanMouth"))
  ([dice-roll]
   (create-test-combatant "dogman" "MeanMouth" dice-roll)))

(defn- reset-atoms! []
  (reset! combatants-rolled {})
  (reset! combatants-received {})
  (reset! encounter-id nil))

(defn my-test-fixture [f]
  (reset-atoms!)
  (f))

(use-fixtures :each my-test-fixture)

(deftest test-default-counts
  (is (= 0 (count @combatants-rolled)))
  (is (= 0 (count @combatants-received))))

(deftest test-that-combatant-service-can-be-hit
  (is (not (nil? (get-initiative-bonus (create-mean-mouth))))))

(deftest test-get-correct-initiative
  (with-redefs [get-initiative-bonus (fn [id] 25)]
    (is (= 25 (get-initiative-bonus (create-mean-mouth))))))

(deftest test-get-initiative
  (testing "numeric dice roll"
    (with-redefs [get-initiative-bonus (fn [id] -1)]
      (is (= 17 (get-initiative-value (create-silver-noun 18)))))

    (with-redefs [get-initiative-bonus (fn [id] 2)]
      (is (= 20 (get-initiative-value (create-silver-noun 18))))
      (is (= 9 (get-initiative-value (create-silver-noun 7))))))

  (testing "string dice roll"
    (with-redefs [get-initiative-bonus (fn [id] -1)]
      (is (= 17 (get-initiative-value (create-silver-noun (str 18))))))

    (with-redefs [get-initiative-bonus (fn [id] 2)]
      (is (= 20 (get-initiative-value (create-silver-noun (str 18)))))
      (is (= 9 (get-initiative-value (create-silver-noun (str 7))))))))

(deftest test-process-single-initiative
  (initialize-received-combatants {:encounterId 69,
                                   :combatants [{:userId "jason", :combatantName "SilverNoun"},
                                                {:userId "dogman", :combatantName "MeanMouth"}]})

  (with-redefs [get-initiative-bonus (fn [id] 0)]
    (process-single-initiative {:userId "jason" :combatantName "SilverNoun" :diceRoll 13}))

  (is (= '("SilverNoun") (keys @combatants-rolled)))
  (let [silver-noun-actual (@combatants-rolled "SilverNoun")]
    (is (= 13 (:initiative silver-noun-actual)))
    (is (= "SilverNoun" (:combatantName silver-noun-actual)))
    (is (= "jason" (:userId silver-noun-actual)))
    (is (= 13 (:diceRoll silver-noun-actual))))

  (with-redefs [get-initiative-bonus (fn [id] 3)]
    (process-single-initiative {:userId "dogman", :combatantName "MeanMouth" :diceRoll 7}))
  (is (= '("MeanMouth" "SilverNoun") (keys @combatants-rolled)))

  (let [mean-mouth-actual (@combatants-rolled "MeanMouth")]
    (is (= 10 (:initiative mean-mouth-actual)))
    (is (= "MeanMouth" (:combatantName mean-mouth-actual)))
    (is (= "dogman" (:userId mean-mouth-actual)))
    (is (= 7 (:diceRoll mean-mouth-actual)))))

;; (deftest test-process-single-combatant-one-roll-one-character
;;   (is true)
;;   )

(deftest test-should-allow-combatant-roll?
  (let [silver-noun {:userId "jason" :combatantName "SilverNoun" :diceRoll 11}]
    (testing "is-combatant-in-combatants-received? no"
      (reset-atoms!)
      (is (= false (is-combatant-in-combatants-received? silver-noun))))

    (testing "is-combatant-in-combatants-received? yes"
      (reset-atoms!)
      (swap! combatants-received assoc "SilverNoun" silver-noun)
      (is (= true (is-combatant-in-combatants-received? silver-noun))))

    (testing "has-combatant-already-rolled? no"
      (reset-atoms!)
      (is (= 0 (count @combatants-rolled)))
      (is (= false (has-combatant-rolled? silver-noun))))

    (testing "has-combatant-already-rolled? yes"
      (reset-atoms!)
      (swap! combatants-rolled assoc "SilverNoun" silver-noun)
      (is (= true (has-combatant-rolled? silver-noun))))

    (testing "shouldn't allow - DNE in combatants-received"
      (reset-atoms!)
      (is (= 0 (count @combatants-received)))
      (is (= false (should-allow-combatant-roll? silver-noun))))

    (testing "should allow"
      (reset-atoms!)
      (is (= 0 (count @combatants-received)))
      (swap! combatants-received assoc "SilverNoun" silver-noun)
      (is (= 1 (count @combatants-received)))
      (is (= true (should-allow-combatant-roll? silver-noun))))

    (testing "shouldn't allow - already added/exists in combatants-rolled"
      (reset-atoms!)
      (swap! combatants-rolled assoc "SilverNoun" silver-noun)
      (swap! combatants-received assoc "SilverNound" silver-noun)
      (is (= 1 (count @combatants-rolled)))
      (is (= 1 (count @combatants-received)))
      (is (= false (should-allow-combatant-roll? silver-noun))))
    ))


(deftest test-create-initiative
  (let [stankypants {:userId "boolner" :combatantName "stankypants" :initiative 18}
        silverparts {:userId "jason"   :combatantName "silverparts" :initiative 5}
        meanmouth   {:userId "dogman"  :combatantName "MeanMouth" :initiative 13}
        dragon      {:userId "jim"     :combatantName "dragon" :initiative 17}

        unordered-combatants {"stankypants" stankypants
                              "dragon" dragon
                              "meanmouth" meanmouth
                              "silverparts" silverparts}

        expected {"stankypants" stankypants
                  "dragon" dragon
                  "meanmouth" meanmouth
                  "silverparts" silverparts}]

    (testing "with no collisions"
      (is (= expected (create-initiative unordered-combatants))))))

;; (testing "with collisions at end"
;;   (let [tied-with "jason"
;;         tied-name "someBimbo"
;;         actual-initiative (create-initiative (assoc unordered-initiative tied-name (unordered-initiative tied-with)))]
;;     (is (= "goblin" (nth actual-initiative 0)))
;;     (is (= "silverstank" (nth actual-initiative 1)))
;;     (is (= "dragon" (nth actual-initiative 2)))
;;     (is (= #{tied-with tied-name} (set (take-last 2 actual-initiative))))))

;; (testing "with collisions at beginning"
;;   (let [tied-with "goblin"
;;         tied-name "someBimbo"
;;         actual-initiative (create-initiative (assoc unordered-initiative tied-name (unordered-initiative tied-with)))]
;;     (is (= "jason" (nth actual-initiative 4)))
;;     (is (= "silverstank" (nth actual-initiative 2)))
;;     (is (= "dragon" (nth actual-initiative 3)))))

(deftest test-who-hasnt-rolled
  (reset! combatants-received {"stankypants" {:userId "boolner" :combatantName "stankypants"},
                               "MeanMouth"   {:userId "dogman"  :combatantName "MeanMouth"},
                               "dragon"      {:userId "jim"     :combatantName "dragon"},
                               "someDude"    {:userId "shook"   :combatantName "someDude"},
                               "goblin1"     {:userId "jim"     :combatantName "goblin1"}})
  (testing "no one's rolled yet"
    (is (= #{"goblin1" "stankypants" "MeanMouth" "dragon" "someDude"} (set (who-hasnt-rolled?)))))

  (testing "goblin has rolled"
    (reset! combatants-rolled {"goblin1" {:userId "jim", :combatantName "goblin1", :diceRoll 10}})
    (let [actual (who-hasnt-rolled?)]
      (is (= #{"stankypants" "MeanMouth" "dragon" "someDude"} actual))))

  (testing "everyone has rolled"
    (reset! combatants-rolled {"goblin1" {} "stankypants" {} "MeanMouth" {} "dragon" {} "someDude" {}})
    (is (= #{} (who-hasnt-rolled?)))))

(deftest test-process-initiative-created
  (let [stankypants {:userId "boolner" :combatantName "stankypants" :diceRoll 18}
        meanmouth   {:userId "dogman"  :combatantName "meanmouth" :diceRoll 13}
        dragon      {:userId "jim"     :combatantName "dragon" :diceRoll 17}
        silverparts {:userId "jason"   :combatantName "silverparts" :diceRoll 5}]
    (reset! combatants-received {"stankypants" stankypants
                                 "meanmouth" meanmouth
                                 "dragon" dragon
                                 "silverparts" silverparts})
    (reset! encounter-id 69)

    (let [expected (json/write-str {:encounterId 69
                                    :orderedCombatants {"stankypants" (assoc stankypants :initiative 20 :diceRoll 18)
                                                        "dragon" (assoc dragon :initiative 19 :diceRoll 17)
                                                        "meanmouth" (assoc meanmouth :initiative 15 :diceRoll 13)
                                                        "silverparts" (assoc silverparts :initiative 7 :diceRoll 5)}})]
      (with-redefs [get-initiative-bonus (fn [id] 2)]
        (is (= nil (process-initiative-created meanmouth)))
        (is (= nil (process-initiative-created dragon)))
        (is (= nil (process-initiative-created silverparts)))
        (is (= nil (process-initiative-created dragon)))
        (is (= expected (process-initiative-created stankypants)))))))

(deftest test-initialize-received-combatants
  (testing "with raw-string"
    (let [encounter-created-payload {:encounterId 69, :combatants [{:dogman {:maxHP "4", :combatantName "dogman", :user "Jim"}}]}]

      ))

  (testing "with non-empty userId"
    (let [encounter-created-payload {:encounterId 69,
                                     :combatants [{:userId "jim", :combatantName "DTL Boss"},
                                                  {:userId "boolnerbroni", :combatantName "Grook"}] }]
      (reset-atoms!)
      (initialize-received-combatants encounter-created-payload)
      (is (= {"DTL Boss" {:userId "jim", :combatantName "DTL Boss"}
              "Grook"    {:userId "boolnerbroni", :combatantName "Grook"}} @combatants-received))
      (is (= 69 @encounter-id))))

  (testing "with empty userId"
    (let [encounter-created-payload {:encounterId 69,
                                     :combatants [{:userId nil, :combatantName "DTL Boss"},
                                                  {:userId nil, :combatantName "Grook"}] }]
      (reset-atoms!)
      (initialize-received-combatants encounter-created-payload)
      (is (= {"DTL Boss" {:userId nil, :combatantName "DTL Boss"}
              "Grook"    {:userId nil, :combatantName "Grook"}} @combatants-received))
      (is (= 69 @encounter-id)))))


(deftest test-that-only-first-roll-counts
  (reset-atoms!)

  (with-redefs [get-initiative-bonus (fn [id] 0)]
    (let [silver-noun {:userId "jason", :combatantName "SilverNoun"}
          mean-mouth {:userId "dogman", :combatantName "MeanMouth"}
          get-dice-roll (fn [needle] (some #(if (= (:combatantName %) needle) %) @combatants-received))]

      (initialize-received-combatants {:combatants [mean-mouth, silver-noun]})

      (is (= 2 (count @combatants-received)))
      (is (= nil (@combatants-rolled "dogman")))

      (process-single-initiative (assoc silver-noun :diceRoll 13))

      (is (= 13 (:initiative (@combatants-rolled "SilverNoun"))))

      (process-single-initiative {:userId "dogman", :combatantName "SilverNoun", :diceRoll 19})
      (is (= 13 (:initiative (@combatants-rolled "SilverNoun")))))))

(deftest test-encounter-id
  (reset-atoms!)
  (testing "is initially nil"
    (is (nil? @encounter-id)))

  (testing "can be defined"
    (let [combatants-json {:encounterId 69, :gmCombatants ["jason"], :playerCombatants ["fartman"], :combatants ["dogman"]}]
      (initialize-received-combatants combatants-json)
      (is (= 69 @encounter-id)))))

(deftest test-published-messages-are-picked-up-correctly
  "These are hacked with a wait for half a second. "

  (let [encounter-created-payload {:encounterId 69,
                                   :combatants [{:userId "jim", :combatantName "DTL Boss"},
                                                {:userId "boolnerbroni", :combatantName "Grook"}] }]

    (testing "with encounter-created: exists no initial combatants"
      (Thread/sleep 500)
      (is (= 0 (count @combatants-received))))

    (testing "with encounter-created exist 2 combatants"
      (wcar* (car/publish "encounter-created" (json/write-str encounter-created-payload)))
      (Thread/sleep 500)
      (is (= 2 (count @combatants-received))))))
