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

;; (comment
;;   need to add UserId & CombatantName
;;   expect these from encounter service
;;   these need to be in published payloads
;;   ;; {"encounterId": 69, "orderedCombatantIds": [{"userId":"Jason", "combatantName":"SilverNoun"},{"userId":"Jim", "combatantName":"Goblin 1"},{"userId":"Jim", "combatantName":"Goblin 1"}]}
;;   )


(defn my-test-fixture [f]
  (reset! combatants-rolled {})
  (reset! combatants-received #{})
  (f))

(use-fixtures :each my-test-fixture)

(deftest test-default-counts
  (is (= 0 (count @combatants-rolled)))
  (is (= 0 (count @combatants-received))))

(deftest test-that-combatant-service-can-be-hit
  (is (not (nil? (get-initiative-bonus "dogman")))))

(deftest test-get-correct-initiative
  (with-redefs [get-initiative-bonus (fn [id] "25")]
    (is (= "25" (get-initiative-bonus "dogman")))))

(deftest test-get-initiative
  (with-redefs [get-initiative-bonus (fn [id] -1)]
    (is (= 17 (get-initiative "jason" 18))))
  (with-redefs [get-initiative-bonus (fn [id] 2)]
    (is (= 20 (get-initiative "jason" 18)))
    (is (= 9 (get-initiative "jason" 7)))))

(deftest test-process-single-initiative
  (reset! combatants-received #{"jason" "dogman"})
  (let [char-1-json {:playerId "jason", :diceRoll 13}
        char-2-json {:playerId "dogman", :diceRoll 7}]

    (with-redefs [get-initiative-bonus (fn [id] 3)]
      (process-single-initiative char-1-json))
    (is (= {"jason" 16} @combatants-rolled))

    (with-redefs [get-initiative-bonus (fn [id] -2)]
      (process-single-initiative char-2-json))
    (is (= {"jason" 16, "dogman" 5} @combatants-rolled))))

(deftest test-process-single-combatant-one-roll-one-character
  (reset! combatants-received #{"jason"})
  (with-redefs [get-initiative-bonus (fn [id] -2)]
    (is (= {"jason" 11}
           (process-single-initiative {:playerId "jason", :diceRoll 13})))))

(deftest test-create-initiative
  (let [unordered-initiative-map {"jason" 10, "goblin" 20, "dragon" 13, "silverstank" 18}]
    (testing "with no collistions"
      (is (= ["goblin" "silverstank" "dragon" "jason"] (create-initiative unordered-initiative-map))))
    (testing "with collisions at end"
      (let [tied-with "jason"
            tied-name "someBimbo"
            actual-initiative (create-initiative (assoc unordered-initiative-map tied-name (unordered-initiative-map tied-with)))]
        (is (= "goblin" (nth actual-initiative 0)))
        (is (= "silverstank" (nth actual-initiative 1)))
        (is (= "dragon" (nth actual-initiative 2)))
        (is (= #{tied-with tied-name} (set (take-last 2 actual-initiative))))))
    (testing "with collisions at beginning"
      (let [tied-with "goblin"
            tied-name "someBimbo"
            actual-initiative (create-initiative (assoc unordered-initiative-map tied-name (unordered-initiative-map tied-with)))]
        (is (= "jason" (nth actual-initiative 4)))
        (is (= "silverstank" (nth actual-initiative 2)))
        (is (= "dragon" (nth actual-initiative 3)))
        (is (= #{tied-with tied-name} (set (take 2 actual-initiative))))))))

(deftest test-who-hasnt-rolled
  (reset! combatants-received #{"stankypants" "dogman" "dragon" "goblin" "someDude"})
  (testing "no one's rolled yet"
    (is (= #{"stankypants" "dogman" "dragon" "goblin" "someDude"} (who-hasnt-rolled?))))
  (testing "goblin has rolled"
    (reset! combatants-rolled {"goblin" 10})
    (is (= @combatants-rolled {"goblin" 10}))
    (is (= #{"stankypants" "dogman" "dragon" "someDude"} (who-hasnt-rolled?))))
  (testing "everyone has rolled"
    (reset! combatants-rolled {"goblin" 10, "stankypants" 2, "dogman" 3, "dragon" 33, "someDude" 18})
    (is (= #{} (who-hasnt-rolled?)))))

(deftest test-process-initiative-created
  (reset! combatants-received #{"stankyPants" "dogman" "someDude"})
  (reset! encounter-id 69)
  (let [jason {:playerId "jason", :diceRoll 13}
        goblin {:playerId "goblin", :diceRoll 20}
        dogman {:playerId "dogman", :diceRoll 9}
        stanky-pants {:playerId "stankyPants", :diceRoll 4}
        some-dude {:playerId "someDude", :diceRoll 8}
        expected (json/write-str {:encounterId 69, :orderedCombatantIds ["dogman" "someDude" "stankyPants"]}) ]
    (is (= nil (process-initiative-created jason)))
    (is (= nil (process-initiative-created goblin)))
    (is (= nil (process-initiative-created dogman)))
    (is (= nil (process-initiative-created some-dude)))
    (is (= expected (process-initiative-created stanky-pants)))))

(deftest test-initialize-received-combatants
  (testing "only gmCombatants"
    (reset! combatants-received #{})
    (initialize-received-combatants {:gmCombatants ["jason" "dogman" "fartman"] :playerCombatants []})
    (is (= #{"jason" "dogman" "fartman"} (set @combatants-received))))

  (testing "only playerCombatants"
    (reset! combatants-received #{})
    (initialize-received-combatants {:playerCombatants ["jason" "dogman" "fartman"] :gmCombatants []})
    (is (= #{"jason" "dogman" "fartman"} (set @combatants-received))))

  (testing "only combatants"
    (reset! combatants-received #{})
    (initialize-received-combatants {:combatants ["jason" "dogman" "fartman"] :gmCombatants []})
    (is (= #{"jason" "dogman" "fartman"} (set @combatants-received))))

  (testing "all 3 combatants"
    (reset! combatants-received #{})
    (initialize-received-combatants {:gmCombatants ["jason"] :playerCombatants ["fartman"]  :combatants ["dogman"]})
    (is (= #{"jason" "dogman" "fartman"} (set @combatants-received)))))

(deftest test-that-only-first-roll-counts
  (reset! combatants-received #{})

  (with-redefs [get-initiative-bonus (fn [id] 0)]

    (initialize-received-combatants {:combatants ["dogman", "fartman"]})
    (is (= 2 (count @combatants-received)))
    (is (= nil (@combatants-rolled "dogman")))

    (process-single-initiative {:playerId "dogman", :diceRoll 13})
    (is (= 13 (@combatants-rolled "dogman")))

    (process-single-initiative {:playerId "dogman", :diceRoll 19})
    (is (= 13 (@combatants-rolled "dogman")))))

(deftest test-that-unknown-player-cant-roll-initiative
  (reset! combatants-received #{})

  (initialize-received-combatants {:combatants ["dogman"]})
  (is (= nil (@combatants-rolled "dogman")))

  (process-single-initiative {:playerId "fartman" :diceRoll 19})
  (is (= nil (@combatants-rolled "fartman"))))

(deftest test-encounter-id
  (testing "is initially nil"
    (is (nil? @encounter-id)))

  (testing "can be defined"
    (let [combatants-json {:encounterId 69, :gmCombatants ["jason"], :playerCombatants ["fartman"], :combatants ["dogman"]}]
      (initialize-received-combatants combatants-json)
      (is (= 69 @encounter-id)))))
