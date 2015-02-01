(ns shartfinder-initiative-service.utils-test
  (:require [clojure.test :refer :all]
            [shartfinder-initiative-service.utils :refer :all]))

(deftest test-sort-map-by-value
  (let [unsorted-map {"apple" 1, "jason" 20, "dogman" 13}
        unsorted-map-with-dups (assoc unsorted-map "goblin" 1)]
    (testing "unique values"
      (is (=  ["jason" "dogman" "apple"] (keys (sort-map-by-value unsorted-map)))))
    (testing "duplicate values"
      (let [actual (keys (sort-map-by-value unsorted-map-with-dups))]
        (is (= "jason" (nth actual 0)))
        (is (= "dogman" (nth actual 1)))
        (is (= #{"apple" "goblin"} (set (take-last 2 actual))))))))
