(ns ^:figwheel-always figgy.core
  (:require-macros [cljs.core.async.macros :refer [go]])  
  (:require [cljs.core.async :refer [put! chan timeout <! >! sliding-buffer]]
            [goog.events :as events]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:title "Clojurescript Snake"
                          :position '([3 5] [2 5] [1 5])
                          :length 3
                          :direction :right
                          :apples []}))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(def ctx (.getContext (.getElementById js/document "canvas") "2d"))

(defn random-color
  []
  (str "rgb(" (rand-int 255) "," (rand-int 255) "," (rand-int 255) ")"))

(defn draw-box
  [ctx x y size color]
  (set! (.-fillStyle ctx) color)
  (.fillRect ctx x y size size))

(def pi (.-PI js/Math))

(def circ-angle (+ pi (* 3 pi)))

(defn draw-circle [ctx x y size color]
  (set! (.-fillStyle ctx) color)
  (.beginPath ctx)
  (.arc ctx x y size 0 circ-angle)
  (.fill ctx))

(def raf-chan (chan (sliding-buffer 1)))

(defn raf-cb
  [start-time timestamp]
  (let [elapsed (- timestamp @start-time)]
    (reset! start-time timestamp)
    (go (>! raf-chan elapsed))))

(def start-time (atom 0))

(defn raf-loop [timestamp]
  (do
    (raf-cb start-time timestamp)
    (.requestAnimationFrame js/window raf-loop)))

(defn start-raf
  []
  (.requestAnimationFrame js/window raf-loop))

(raf-loop 0)
#_(start-raf)

(def draw (chan))

; stolen from swannodette
; I should adjust my listen
; funcs to return channels vs
; defining global vars
(defn listen [el type]
  (let [out (chan)]
    (events/listen el type
      (fn [e] (put! out e)))
    out))

(def directions {:left [-1 0]
                 :up   [0 -1]
                 :right [1 0]
                 :down [0 1]})

; keyboard handling code
(let [keypress (listen js/window "keypress")]
  (go (while true
        (let [key-code (.-charCode (<! keypress)) dir (:direction @app-state)]
          (case key-code
                37 (when (contains? #{:up :down} dir) (swap! app-state #(assoc-in % [:direction] :left)))
                38 (when (contains? #{:left :right} dir) (swap! app-state #(assoc-in % [:direction] :up)))
                39 (when (contains? #{:up :down} dir) (swap! app-state #(assoc-in % [:direction] :right)))
                40 (when (contains? #{:left :right} dir) (swap! app-state #(assoc-in % [:direction] :down)))
                nil)))))

(defn pos-add [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn calc-move [{len :length apps :apples pos :position dir :direction}]
  {:title "It worked!"
   :length len
   :position (take len (cons (pos-add (first pos) (directions dir)) pos))
   :direction dir
   :apples apps})

(defn draw-snake [parts]
  (doseq [[x y] parts]
    (draw-box ctx (* 25 x) (* 25 y) 25 "rgb(0,200,0)")))

(defn gen-apple []
  (swap! app-state (fn [state] (assoc-in state [:apples] (conj (:apples state) [(rand-int 25) (rand-int 25)])))))

(defn draw-apples [apples]
  (doseq [[x y] apples]
    (draw-box ctx (* 25 x) (* 25 y) 25 "rgb(200,0,0)")))

(defn add-segment []
  (swap! app-state (fn [state] (assoc-in state [:length] (inc (:length state))))))

(defn collision [apples snake]
  (doseq [apple apples]
    (if (= apple snake)
      (add-segment))))

; draw loop (and calcs positions etc
(go
  (while true
    (let [i (<! draw)]
      (.clearRect ctx 0 0 500 500)
      (when (= 0 (rem i 100)) (do (println "Apple!") (gen-apple)))
      (draw-snake (:position @app-state))
      (draw-apples (:apples @app-state))
      (collision (:apples @app-state) (first (:position @app-state)))
      (swap! app-state calc-move))))

; puts frame-number on draw channel
; call-every milliseconds
; it's based on time (not frames) but it only spits a number to draw
(defn start-loop [call-every]
  (go
    (while true
      (loop [elapsed (<! raf-chan)
             acc 0
             i 0]
        (if (> acc call-every)
          (do
            (>! draw i)
            (recur (<! raf-chan) 0 (inc i)))
          (recur (<! raf-chan) (+ elapsed acc) i))))))

(set! (.-innerText (.getElementById js/document "fancy")) (:title @app-state))
(start-loop 150)
