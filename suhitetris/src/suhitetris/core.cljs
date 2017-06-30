(ns suhitetris.core
  (:require [reagent.core :as r]
            [suhitetris.vector :as v]))

(enable-console-print!)



;; global parameters

(def board-height 20)
(def board-width 10)
(def starting-pos [0 5])
(def initial-speed 1000)



;; tetris artefacts

(def pieces [:z :s :t :l :j :i :o])

;; 0 0 currently has to be in 1st row
(def shapes {:t [[0 -1] [0 0] [0 1]
                        [1 0]]
             :s [       [0 0] [0 1]
                 [1 -1] [1 0]]
             :z [[0 -1] [0 0]
                        [1 0] [1 1]]
             :l [[0 -1] [0 0] [0 1] 
                 [1 -1]]
             :j [[0 -1] [0 0] [0 1] 
                              [1 1]]
             :i [[0 -1] [0 0] [0 1] [0 2]]
             :o [[0 0] [0 1]
                 [1 0] [1 1]]})



;; default generators for artefacts

(defn random-piece []
  (rand-nth pieces))

(defn empty-line []
  (into [] (repeat board-width 0)))

(defn empty-board []
  (into [] (repeat board-height (empty-line))))



;; rules
(defn level-for [total-line-clears]
  (min 30 (int (/ total-line-clears 3))))

(defn speed-for [min-speed level]
  (- min-speed (* level 30)))

(defn score-for [lines level]
  (condp = lines
    0 0
    1 (* 40 (+ level 1))
    2 (* 100 (+ level 1))
    3 (* 300 (+ level 1))
    4 (* 1200 (+ level 1))))


(defn initial-state
  ([] {:board (empty-board)
                      :block {:position starting-pos
                              :shape (shapes (random-piece))}
                      :alive false
                      :line-clears 0
                      :score 0
                      :settings {:gravity true
                                 :debug false}})
  ([prev-state] {:board (empty-board)
                      :block {:position starting-pos
                              :shape (shapes (random-piece))}
                 :alive true
                 :line-clears 0
                 :score 0
                 :settings {:gravity (get-in prev-state [:settings :gravity])
                            :debug false}}))

;; app state
(defonce app-state (r/atom (initial-state)))



;; calculation helpers

(defn positions [block]
  (mapv (partial v/vec+ (:position block)) (:shape block)))

(defn merge-block [board block val]
    (reduce #(assoc-in %1 %2 val) board (positions block)))


;; dependent states

(defn level [state]
  (level-for (:line-clears state)))

(defn speed [state]
  (speed-for initial-speed (level state)))

(defn valid? [state]
  (let [blocks (positions (get-in state [:block]))
        board  (get-in state [:board])]
    (every? #(= (get-in board %) 0) blocks)))



;; actions

;; todo: random rotation + choose position depending on shape
(defn respawn [state]
  (let [next-state (-> (assoc-in state [:block :position] starting-pos)
                       (assoc-in [:block :shape] (shapes (random-piece))))]
    (if (valid? next-state) next-state
      (assoc-in state [:alive] false))))


(defn- line-clears-in [board]
  (- board-height (count board)))

(defn- fill-empty-blocks [board]
  (into [] (-> (repeat (line-clears-in board) (empty-line))
               (concat (apply list board)))))

(defn update-score [state]
  (let [board             (:board state)
        line-clears       (line-clears-in board)
        total-line-clears (+ line-clears (:line-clears state))
        level             (level-for total-line-clears)]
    (-> (update-in state [:score] #(+ % (score-for line-clears level)))
        (update-in [:line-clears] (partial + line-clears)))))

(defn remove-full-lines [board]
  (into [] (remove (partial = (repeat board-width 1)) board)))

(defn kill-block [state]
  (update-in state [:board] #(merge-block % (:block state) 1)))



;; interactions

(defn move-down [state]
  (let [next-state (update-in state [:block :position] v/move-down)]
    (if (valid? next-state) next-state
      (-> (kill-block state)
          (update-in [:board] remove-full-lines)
          (update-score)
          (update-in [:board] fill-empty-blocks)
          (respawn)))))   

(defn move-left [state]
  (let [next-state (update-in state [:block :position] v/move-left)]
    (if (valid? next-state) next-state      
      state)))

(defn move-right [state]
  (let [next-state  (update-in state [:block :position] v/move-right)]
    (if (valid? next-state) next-state
      state)))

(defn rotate-left [state]
  (let [next-state (update-in state [:block :shape] #(mapv v/rotl %))]
    (if (valid? next-state) next-state
      state)))

(defn rotate-right [state]
   (let [next-state (update-in state [:block :shape] #(mapv v/rotr %))]
    (if (valid? next-state) next-state
      state)))

(defn reset [state]
  (-> (assoc-in state [:board] (empty-board))
      (assoc-in [:alive] true)
      (assoc-in [:score] 0)))


;; ticker

(defn drop! [] (js/setTimeout 
                 #(do (when 
                        (and (:alive @app-state) 
                             (get-in @app-state [:settings :gravity])) 
                        (swap! app-state move-down))
                      (drop!))
                 (speed @app-state)))

(defonce gravity! (drop!))  



;; ui

(defn start-overlay [visible resetfn]
  [:div
    [:input {:type :button
             :class (clojure.string/join " " (vector "start-button" (if visible "visible" "invisible")))
             :value (str "Yeah you are dead! Revive?")
             :on-click resetfn}]])
  

(defn level-display [level]
  [:div.line {:class "board-display"} "level? " level "!"])
   

(defn score-display [score]
  [:div.line {:class "board-display"} "score? " score "!"])

(defn line-of-blocks [index values]
  ^{:key index}[:div.line
                (map-indexed (fn [i v]
                               ^{:key i}[:div.cell (cond (= v 1) {:class "dead"}                                          
                                                         (= v 2) {:class "alive"}
                                                         :else   {:class "empty"})])
                  values)])

(defn tetris []  
  [:div.game
    [:div.container 
     [:div.board
      (map-indexed line-of-blocks (merge-block (:board @app-state) (:block @app-state) 2))
      [score-display (:score @app-state)]
      [level-display (level @app-state)]
      [start-overlay (not (:alive @app-state)) #(reset! app-state (initial-state))]]]
                           
   (when (get-in @app-state [:settings :debug])
     [:div.debug
      [:input {:type :checkbox
               :name "Gravity"
               :defaultChecked true
               :on-click #(swap! app-state update-in [:settings :gravity] not)}]
      [:div (str @app-state)]
      [:div "level: " (level @app-state)]
      [:div "speed: " (speed @app-state)]])]) 





(defn ^:export mount-tetris [element-id]
  (r/render-component [tetris] 
    (.getElementById js/document element-id)))

(defn on-js-reload [] 
  (mount-tetris "app"))

(defn handle-arrow-keys! [event]
  (let [key-code (.-keyCode event)]
    (when (:alive @app-state)
      (case key-code
        37 (swap! app-state move-left)
        32 (swap! app-state rotate-left)
        38 (swap! app-state rotate-right)
        39 (swap! app-state move-right)
        40 (swap! app-state move-down)
        :default))))

(defonce listener (.addEventListener js/document "keydown" handle-arrow-keys!))

(mount-tetris "app")

