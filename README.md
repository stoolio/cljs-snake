# Snake in Clojurescript

This is canvas based, but I would like to properly strip out the logic so you could have an ascii version or something. That would be fun.

Anyways, you can move the snake. It stops when you hit the "walls" and he grows when you pick up a red block (apple?). You can also crash into yourself.

One bit of wierdness, apples positions are picked randomly, and if they appear where the snake currently is, something strange happens. They should get redrawn, but some debugging is in order. Still, the logical fix is to prevent them from having a current position occupied by something else.

Arrow keys move.

#### Super Secret Debug Keys

* `r` : reloads game and starts over without refresh
* `-` : slows things down (not the numpad one)
* `+` : speeds things up (again, no numpad)

## What!?!

Install leiningen if you don't have it, `git clone` the repo. `lein figwheel` and have fun.

## Is it fun?

It's almost fun.

The basics are implemented. Now, scaling difficulty, levels, and more fun are on the horizon. Or, fancy CSS3 transitions. Try it in the dev tools, it's pretty cool.

## The Rest

I've improved the loop quite a bit and I am pretty happy with it. I also removed an extraneous loop I had farther up the file, along with lots of cleanup of other gunk.

I'm new at this clojure thing, but thoroughly enjoying the ride.

A `timer` func uses timeout channels and is used by a `render-loop` function to throttle itself. It blocks until it's time, and then it hooks another function on `requestAnimationFrame`. That function puts a vector of the elapsed time and the current frame # onto a render channel. The main render loop block on the render channel to properly render. It looks nice and seems to work okay, but I still have to see what happpens if we dropped frames (although it should be fairly safe, it doesn't need to run anywhere near 60fps).

There is some tricks with the input loop, which I will explain at a later date. The main key event channel (for the arrow keys) is designed in a way so you can't go backwards onto yourself by hitting multiple arrow keys in between frames. In addtion, it allows the magic snake move of hitting two keys in succession to make quick turns. Basically, only one key event is pulled off the channel per render frame. The key channel is also sliding, so old events are dropped.

My biggest concern previously was the render loop, and it (and the input) feel good and have the old timey snake feel. You really have to try it, it's hard to explain.
