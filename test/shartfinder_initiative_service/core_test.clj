(ns shartfinder-initiative-service.core-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [shartfinder-initiative-service.core :refer :all]))

(defn non-threaded []
  (let [a (+ 1 1)
        b (- 1000 a)
        c (* 3.9 (/ 13 b))]
    c))

(defn non-threaded-no-let []
  (* 3.9
     (/ 13
        (- 1000
           (+ 1 1)))))

(defn threaded []
  (->> 1
       (+ 1)
       (- 1000)
       (/ 13)
       (* 3.9)))

(deftest how-does-threading-work?
  (is (= (non-threaded) (non-threaded-no-let)))
  (is (= (non-threaded) (threaded))))


(def this-content-json
  (json/read-str
   "{\"id\":6,\"name\":\"Goblin encounter\",\"gmCombatants\":[\"goblin1\",\"goblin2\",\"ogre\"],\"playerCombatants\":[\"tom\",\"jason\"]}"))

(deftest test-create-initiative-no-collisions
  (let [dice-outcomes [1 2 3 4 5]]
    (is (= ["jason" "tom" "ogre" "goblin2" "goblin1"] (create-initiative this-content-json dice-outcomes)))))

(deftest test-create-initiative-with-collisions
  (let [dice-outcomes [1 4 4 4 5]
        actual-initiative (create-initiative this-content-json dice-outcomes)]
    (is (= "jason" (actual-initiative 0)))
    (is (= "goblin1" (actual-initiative 4)))))

(deftest test-get-players-from-json
  (is (= ["goblin1" "goblin2" "ogre" "tom" "jason"] (get-players-from-json this-content-json))))
