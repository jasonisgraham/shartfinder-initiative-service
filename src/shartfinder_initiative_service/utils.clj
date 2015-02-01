(ns shartfinder-initiative-service.utils
  (:gen-class))

(defn zippy [l1 l2]
  "like zipmap but doesn't creates sets for duplicates keys
   http://stackoverflow.com/questions/17134771/zipmap-with-multi-value-keys"
  (apply merge-with concat (map (fn [a b]{a (list b)}) l1 l2)))

(defn sort-map-by-value [unsorted-map]
  (into (sorted-map-by (fn [key1 key2]
                         (compare [(get unsorted-map key2) key2]
                                  [(get unsorted-map key1) key1])))
        unsorted-map))
