(ns ^:figwheel-always figgy.core
  (:require-macros [cljs.core.async.macros :refer [go]])  
  (:require [cljs.core.async :refer [put! chan timeout <! >! sliding-buffer]]
            [goog.events :as events]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

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

(def raf-chan (chan (sliding-buffer 1)))

#_(defn raf-cb
  [start-time timestamp]
  (let [elapsed (min 1 (- timestamp start-time))]
    (put! raf-chan elapsed)))

(def start-time (atom 0))

(defn raf-loop
  [timestamp]
  (put! raf-chan (- timestamp @start-time))
  (reset! start-time timestamp)
  (.requestAnimationFrame js/window raf-loop))

(defn start-raf
  []
  (.requestAnimationFrame js/window raf-loop))

(start-raf)

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

; direction of movement
; used by draw loop
; changed by keyboard handler
(def direction (atom [1 0]))

; keyboard handling code
(let [keypress (listen js/window "keypress")]
  (go (while true
        (let [key-code (.-charCode (<! keypress))]
          (case key-code
                37 (reset! direction [-1 0])
                38 (reset! direction [0 -1])
                39 (reset! direction [1 0])
                40 (reset! direction [0 1]))))))

; draw loop (and calcs positions etc
(go
  (let [x (atom 0)
        y (atom 0)]
    (while true
    (let [i (<! draw)
          x-dir (first @direction)
          y-dir (last @direction)
          x (reset! x (min 475 (max 0 (+ (* 25 x-dir) @x))))
          y (reset! y (min 475 (max 0 (+ (* 25 y-dir) @y))))]
      (.clearRect ctx 0 0 500 500)
      (draw-box ctx x y 25 (random-color))))))

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

(start-loop 100)
