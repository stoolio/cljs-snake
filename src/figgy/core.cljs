(ns ^:figwheel-always figgy.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])  
  (:require [cljs.core.async :refer [put! chan timeout <! >! sliding-buffer]]
            [goog.events :as events]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(def default-state {:title "Clojurescript Snake"
                    :position '([3 5] [2 5] [1 5])
                    :length 3
                    :direction :right
                    :apples (sorted-set)
                    :running true})

(defonce app-state (atom default-state))

(def speed (atom 250))

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

; stolen from swannodette
(defn listen [el type]
  (let [out (chan (sliding-buffer 1))]
    (events/listen el type
      (fn [e] (put! out e)))
    out))

(def directions {:left [-1 0]
                 :up   [0 -1]
                 :right [1 0]
                 :down [0 1]})

(defn pos-add [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn calc-move [{:keys [length direction]} state]
  (update-in state [:position] 
             (fn [pos]
               (take length (cons (pos-add (first pos) (directions direction)) pos)))))

(defn draw-snake [parts]
  (doseq [[x y] parts]
    (draw-box ctx (* 25 x) (* 25 y) 25 "rgb(0,200,0)")))

(defn gen-apple []
  (swap! app-state (fn [state] (assoc-in state [:apples] (conj (:apples state) [(rand-int 25) (rand-int 25)])))))

(defn draw-apples [apples]
  (doseq [[x y] apples]
    (draw-box ctx (* 25 x) (* 25 y) 25 "rgb(200,0,0)")))

(defn add-segment []
  (swap! app-state update-in [:length] inc))

(defn stop! []
  (swap! app-state assoc-in [:running] false))

(defn out-of-bounds [[x y]]
  (or
    (< x 0)
    (> x 19)
    (< y 0)
    (> y 19)))

(defn remove-apple [apple]
  (swap! app-state update-in [:apples] (fn [a] remove #(= apple %) a)))

(defn collision [apples snakes]
  (cond
    (out-of-bounds (first snakes)) (stop!)
    (not (= (count snakes) (count (set snakes)))) (stop!))
  (doseq [apple apples]
    (when (= apple (first snakes))
      (do
        (remove-apple apple)
        (println "hit: " apple)
        (add-segment)))))

; Warning! cross browser bs ahead
; may explode, handle with care
(defn set-text [selector text]
  (set! (.-textContent (.querySelector js/document selector)) text)
  (set! (.-innerText (.querySelector js/document selector)) text))

(set-text "#speed" @speed)
(add-watch speed :speed-display (fn [_ _ _ new]
                          (set-text "#speed" new)))

(add-watch app-state :stats-display (fn [_ _ _ new]
                              (set-text "#full" (:position new))
                              (set-text "#fancy" (:title new))
                              (set-text "#running" (:running new))
                              (set-text "#apples" (count (:apples new)))
                              (set-text "#length" (:length new))))

(defn timer [time]
  (let [frame (chan)]
    (go-loop []
      (<! (timeout @time))
      (>! frame 0)
      (recur))
    frame))

(defn render-loop [time]
  (let [render (chan)
        frame (timer time)
        start-time (atom (.. js/window -performance now))]
    (go-loop [i 0]
      (.requestAnimationFrame js/window (fn [timestamp]
                                          (go
                                            (>! render [(- timestamp @start-time) i])
                                            (reset! start-time timestamp))))
      (<! frame)
      (recur (inc i)))
    render))

(defn set-direction [dir]
  (swap! app-state assoc-in [:direction] dir))

; keyboard handling code
(let [keypress (listen js/window "keydown") ichan (chan (sliding-buffer 1))]
  (defn get-input []
    (go-loop [key-code (.-keyCode (<! keypress)) dir (<! ichan)]
      (case key-code
            37 (when (contains? #{:up :down} dir) (set-direction :left))
            38 (when (contains? #{:left :right} dir) (set-direction :up))
            39 (when (contains? #{:up :down} dir) (set-direction :right))
            40 (when (contains? #{:left :right} dir) (set-direction :down))
            nil)
      (recur (.-keyCode (<! keypress)) (<! ichan)))
    ichan))

(defn start! []
  (let [render (render-loop speed) ichan (get-input)]
    (go (while (:running @app-state)
          (let [[_ i] (<! render)]
            (.clearRect ctx 0 0 500 500)
            (draw-snake (:position @app-state))
            (draw-apples (:apples @app-state))
            (swap! app-state calc-move)
            (collision (:apples @app-state) (:position @app-state))
            (>! ichan (:direction @app-state))
            (when (= 0 (rem i 24)) (gen-apple)))))))

; debug key handling
(let [keypress (listen js/window "keydown")]
  (go-loop [key-code (.-keyCode (<! keypress)) dir (:direction @app-state)]
    (case key-code
          187 (swap! speed #(max 50 (- % 25)))
          189 (swap! speed #(min 500 (+ 25 %)))
          82 (do
               (when-not (:running @app-state) (start!))
               (reset! app-state default-state))
          nil)
    (recur (.-keyCode (<! keypress)) (:direction @app-state))))

(start!)
