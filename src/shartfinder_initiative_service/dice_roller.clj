(ns shartfinder-initiative-service.dice-roller
  (:gen-class))

(defn- get-initiative-bonus [_] 0)

(defn roll-dice
  ;; here the 1st arg is the combatant, but since we're not looking at a CombatantService yet, just ignore this var
  ([_] (roll-dice _ 20))
  ([_ dice-size]
   (let [initiative-bonus (get-initiative-bonus _)])
   (inc (rand-int dice-size))))
