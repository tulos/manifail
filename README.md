# manifail

Handle failures and manage retries without callbacks!

The semantics are modeled closely to
[Failsafe](https://github.com/jhalterman/failsafe), which has a Clojure wrapper
called [Diehard](https://github.com/sunng87/diehard).

Manifail differs in that it tries to be composable and not rely on callbacks
for the failure handling logic. The drawback is that it's less precise and
carries more overhead than Failsafe. Also, circuit breakers are currently not
supported.

## Disclaimer

Use Manifail if you're feeling adventurous and/or like to write your code in a
direct callback-less style! Otherwise please consider using Failsafe directly
or through Diehard.

## Usage

`[tulos/manifail "0.1.0"]`

First, require the namespaces:

```clojure
(refer-clojure :exclude '(delay))
(require '[manifold.deferred :as d])
(require '[manifold.executor :as ex])
(use 'manifail)
```

The simplest possible retry logic that will perform 3 retries with delays of
50, 100 and 150 ms:

```clojure
(with-retries [50 100 150]
  (unreliable-service))
```

The following code will retry the call 5 times with 50 ms delay:

```clojure
(with-retries (delay (retries 5) 50)
  (unreliable-service))
```

same, but with a backoff factor of 2.0:

```clojure
(with-retries (delay (retries 5) 50 {:backoff-factor 2.0})
  (unreliable-service))
```

with a random jitter of 25 ms:

```clojure
(with-retries (delay (retries 5) 50 {:backoff-factor 2.0, :jitter-ms 25})
  (unreliable-service))
```

with a random jitter scaling the delay up to 1/2:

```clojure
(with-retries (delay (retries 5) 50 {:backoff-factor 2.0, :jitter-factor 0.5})
  (unreliable-service))
```

with a total call and retry duration limited to 500 ms:

```clojure
(with-retries (-> (retries 5)
                  (delay 50 {:backoff-factor 2.0, :jitter-factor 0.5})
                  (limit-duration 500))
  (unreliable-service))
```

unlimited equally spaced out retries with duration limited to 500 ms:

```clojure
(with-retries (-> (forever) (delay 50) (limit-duration 500))
  (unreliable-service))
```

### A Complete Example

Below I tried to map callbacks available in Failsafe retry policy to the code
that you could write in order to get equivalent behaviour.  We assume that
`call-some-service` blocks until completed:

```clojure
(->
  (with-retries (delay (retries 5) 50) ;; retry on any exception by default
    (try (let [result (call-some-service)]
           (when (> result 5) ;; abort condition
             (println "-- on failed attempt 1")
             (println "-- on before abort")
             (abort! (ex-info "Result > 5!" {:result result})))
           (when (= result ::bad) ;; retry condition
             (println "-- on failed attempt 2")
             (retry!))
           result)
         (catch OkException _ :ok) ;; do not retry on this exception
         (catch Throwable e
           (println "-- on failed attempt any")
           (throw e))))
  (d/chain #(println "-- on complete" %))
  (d/catch manifail.Aborted #(println "-- on after abort" %))
  (d/catch manifail.RetriesExceeded #(println "-- on retries exceeded" %))
  (d/catch #(println "-- on failure" %)))
```

`call-some-service` might also be asynchronous and return something derefable.
In this case the code within `with-retries` can `chain` the logic:

```clojure
(with-retries (delay (retries 5) 50)
  (-> (call-some-service)
      (d/chain (fn [result] ...))))
```

## License

Copyright © 2016 Tulos Capital

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
