(ns ^:figwheel-always figgy.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])  
  (:require [cljs.core.async :refer [put! take! to-chan chan timeout <! >! sliding-buffer alts!]]
            [clojure.set :refer [difference]]
            [goog.events :as events]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(def default-state {:title "Clojurescript Snake"
                    :position '([3 5] [2 5] [1 5])
                    :length 3
                    :direction :right
                    :apples (sorted-set)
                    :running true})

(def speed (atom 250))

(defonce app-state (atom default-state))

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

(defn draw-circle [ctx x y size color]
  (let [pi (.-PI js/Math)
        circ-angle (+ pi (* 3 pi))]
    (set! (.-fillStyle ctx) color)
    (.beginPath ctx)
    (.arc ctx x y size 0 circ-angle)
    (.fill ctx)))

; stolen from swannodette
(defn listen 
  [el type]
   (let [out (chan (sliding-buffer 1))]
     (events/listen el type
                    (fn [e] (put! out e)))
     out))

; info

(def key->action {37 :left
                  38 :up
                  39 :right
                  40 :down})

(def key->debug {82  :reset
                 187 :+speed
                 189 :-speed})

(def directions {:left [-1 0]
                 :up   [0 -1]
                 :right [1 0]
                 :down [0 1]})

; end info

; utils
; must be pure

(defn pos-add [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

; TODO remove magic numbers
(defn out-of-bounds [[x y]]
  (or
    (< x 0)
    (> x 19)
    (< y 0)
    (> y 19)))

; end utils

(defn timer [time]
  (let [frame (chan)]
    (go-loop [i 0]
      (<! (timeout @time))
      (>! frame i)
      (recur (inc i)))
    frame))

(defn render-loop [time]
  (let [render (chan)
        frame (timer time)
        last-time (atom (.. js/window -performance now))]
    (go-loop [i 0]
      (.requestAnimationFrame js/window (fn [timestamp]
                                          (go
                                            (>! render [(- timestamp @last-time) i])
                                            (reset! last-time timestamp))))
      (recur (<! frame)))
    render))

; start debug
; takes advantage of global state :'(
; should refactor and put in state thread
(do
  (let [keypress (listen js/window "keydown")]
    (go-loop [key-code 0]
      (case key-code
            :-speed (swap! speed #(max 50 (- % 25)))
            :+speed (swap! speed #(min 500 (+ 25 %)))
            :reset (do ; broken :(
                 (when-not (:running @app-state) (start!))
                 (reset! app-state default-state))
            nil)
      (recur (-> (<! keypress)
                 (.-keyCode)
                 (key->debug)))))
  
  ; Warning! cross browser bs ahead
  ; may explode, handle with care
  (defn set-text [selector text]
    (set! (.-textContent (.querySelector js/document selector)) text)
    (set! (.-innerText (.querySelector js/document selector)) text))

  (set-text "#speed" @speed)
  (add-watch speed :speed-display (fn [_ _ _ new]
                                    (set-text "#speed" new)))

  (add-watch app-state :stats-display (fn [_ _ _ new]
                                        (set-text "#points" (or (:points new) 0))
                                        (set-text "#fancy" (:title new))
                                        (set-text "#running" (:running new))
                                        (set-text "#apples" (count (:apples new)))
                                        (set-text "#length" (:length new)))))
; end debug

; implement the dream

(let [keypress (listen js/window "keydown")
      input-chan (chan (sliding-buffer 1))]
  (defn start-input []
    (go-loop [action (-> (<! keypress)
                         (.-keyCode)
                         (key->action :nop))]
             (>! input-chan action)
             (recur (-> (<! keypress)
                         (.-keyCode)
                         (key->action :nop))))
    input-chan))

(defn set-direction [state new-dir]
  (assoc state :direction new-dir))

(let [horiz #{:left :right}
      vert #{:up :down}]
  (defn process-input [{:keys [input direction] :as state}]
    (if input
      (cond
        (and (horiz input) (not (horiz direction))) (set-direction state input)
        (and (vert input) (not (vert direction))) (set-direction state input)
        :default state)
      state)))

(defn stop! [state]
  (assoc state :running false))

(defn add-points [state points]
  (update-in state [:points] + points))

(defn calc-move [{:keys [length direction] :as state}]
  (update-in state [:position] (fn [pos]
                                 (take length (cons (pos-add (first pos) (directions direction)) pos)))))

(defn draw-snake [state]
  (doseq [[x y] (:position state)]
    (draw-box ctx (* 25 x) (* 25 y) 25 "rgb(0,200,0)"))
  state)

(defn generate-apple [state frame]
  (let [total-apples (count (:apples state))
        max-apples (int (/ frame 60))]
    (if (< total-apples max-apples)
      (update-in state [:apples] conj [(rand-int 20) (rand-int 20)])
      state)))

(defn draw-apples [state]
  (doseq [[x y] (:apples state)]
    (draw-box ctx (* 25 x) (* 25 y) 25 "rgb(200,0,0)"))
  state)

(defn add-segment [state eaten]
  (update-in state [:length] + eaten))

(defn remove-apples [state eaten]
  (update-in state [:apples] (fn [apples]
                               (difference apples eaten))))

(defn collided? [pos1 pos2]
  (= pos1 pos2))

(defn bounds|snake [{:keys [position] :as state}]
  (if (out-of-bounds (first position))
    (stop! state)
    state))

(defn snake|snake [{:keys [position] :as state}]
  (let [snakes (count position)
        uniques (count (set position))]
    (if (= snakes uniques)
      state
      (stop! state))))

(defn apples|snake [{:keys [position apples] :as state}]
  (let [snake (first position)
        eaten (filter (partial collided? snake) apples)]
    (-> state
        (remove-apples eaten)
        (add-segment (count eaten))
        (add-points (* (count eaten) 500)))))

(defn collision-check [state]
  (-> state
      (bounds|snake)
      (snake|snake)
      (apples|snake)))

; this has been implemented in the master branch of core.async
; but rather than attempt an upgrade, here is my hacky version
; it must be run in a go block, and you must <! to get the val
; fine for my use
(defn poll! [port]
 (go (let [[res _] (alts! [port (to-chan [])] {:priority true})]
       res)))

(defn debug-state [state]
  (reset! app-state state)
  state)

(defn clear-screen [{:keys [ctx] :as state}]
  (.clearRect ctx 0 0 500 500)
  state)

(defn halp [state msg]
  (println msg)
  state)

; the dream
(defn start! [initial-state ctx speed]
  (let [render (render-loop speed)
        input-chan (start-input)]
    (go-loop [state (merge initial-state {:ctx ctx})
              [_ i] [0 0]]
             (if (:running state)
               (-> (merge state {:input (<! (poll! input-chan))})
                   (clear-screen)
                   (process-input)
                   (draw-snake)
                   (draw-apples)
                   (calc-move)
                   (collision-check)
                   (generate-apple i)
                   (debug-state)
                   (recur (<! render)))
               (recur initial-state (<! render))))))

; end the dream

; currently speed must be an atom
; to facilitate debug speed change
(start! default-state
        (.. js/document (getElementById "canvas") (getContext "2d"))
        speed)
