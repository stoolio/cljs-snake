# Snake in Clojurescript

This is canvas based, but I would like to properly strip out the logic so you could have an ascii version or something. That would be fun

## What!?!

Install leiningen if you don't have it, `git clone` the repo. `lein figwheel` and have fun.

## Is it fun?

Currently you can steer a green snake around. He doesn't collide with the bounds, so you can lost him.

Red blocks appear every so often. If you collide with them, they won't disappear (yet), but your snake will grow.

So, not very fun.

## The Rest

The code is atrocious at this point. I've just hacked the majority of it together over a night and it has too many global vars and magic numbers.

Also, I'm new at this clojure thing, so that isn't helping.

Also, the loop doesn't quite feel right. I've commited to using core.async vs callback hell, so I'm trying to figure out the best way to make that work. In addition, I want classic snake style gameplay. This basically means that the board is a grid, and positions snap to the grid.

Currenly, I drop a raf callback on a channel, which a go loop pulls and calcs elapsed time. I start the loop passing in a time. After elapsed time is > than my value, it puts on another channel. The game loop pulls from that channel. I'm just wrapping my head around core.async, but I can't guarantee you that my drawing code is running during the actual requestAnimationFrame. In addition, since I don't want 60fps, is there a better way to do this so I don't have an eternal callback on requestAnimationFrame?

I was thinking a go loop with a timeout channel. The loop would hook my render func with requestAnimationFrame after it's specified timeout.

Regardless, I think there is a way to still use channels (and completely insulate myself from js interop), but still have a function running on requestAnimationFrame when I'd like. I just don't know the most flexible way to do that.

More research is needed.