(ns ^:figwheel-never figgy.squrs)

#_(draw-box ctx 20 20 "rgb(200,0,0)")

#_(defn random-squares
  []
  (go (loop [n 0]
        (draw-box ctx (rem n 500) (rem n 500) (random-color))
        (<! (timeout 100))
        (recur (+ n 5)))))