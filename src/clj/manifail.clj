(ns manifail
  (:refer-clojure :exclude [delay reset!])
  (:require [manifold
             [deferred :as d]
             [executor :as ex]])
  (:import [clojure.lang ExceptionInfo]
           [manifail Aborted Retried RetriesExceeded Reset]))

(defn forever
  "A sequence of infinite retries with zero delay."
  [] (repeat 0))

(defn retries
  "Creates a sequence of `n` retries with zero delay."
  [n] (take n (forever)))

(defn- verify-factor [v min max name]
  (assert (and v (> v min) (<= v max))
          (format "%s factor must be between %s and %s!" name min max)))

(defn- delay-opts [delay-ms {:keys [max-delay-ms backoff-factor jitter-ms jitter-factor]}]
  (assert (not (and jitter-ms jitter-factor))
          "Cannot have both jitter-ms and jitter-factor specified!")
  (when max-delay-ms (assert (> max-delay-ms 0) "Max delay must be positive!"))
  (when jitter-ms (assert (> jitter-ms 0) "Jitter delay must be positive!"))
  (when jitter-factor (verify-factor jitter-factor 0.0 1.0 "Jitter"))
  (when backoff-factor (verify-factor backoff-factor 0.0 Double/MAX_VALUE "Backoff"))
  {:max-delay-ms (long (or max-delay-ms Long/MAX_VALUE))
   :backoff-factor (if backoff-factor (double backoff-factor) 1.0)
   :jitter-ms (if jitter-ms (long jitter-ms) 0)
   :jitter-factor (if jitter-factor (double jitter-factor) 0.0)})

(defn- compute-jitter [{:keys [jitter-ms jitter-factor]}]
  (let [rand-move #(- 1 (* 2 (rand)))]
    (cond
      (> jitter-ms 0) #(long (+ % (* jitter-ms (rand-move))))
      (> jitter-factor 0) #(long (* % (inc (* jitter-factor (rand-move)))))
      :else identity)))

(defn delay
  "Computes a delay for each retry in the `src` seq according to the `opts`.

  When no `opts` given, every delay will be the same `delay-ms`.

  Possible `opts`:
    * `max-delay-ms` - the maximum delay
    * `backoff-factor` - the factor to multiply each delay with,
      e.g. a factor of 2.0 and delay of 50ms will produce delays of
      50, 100, 200, 400, ...
    * `jitter-ms` - the amount of time to jitter each delay with,
      e.g. a jitter of 25ms and a delay of 50ms will result in delays of
      50, [25; 75], [0; 100], ... or concretely 50, 60, 45, 53, ...
    * `jitter-factor` - the factor between 0 and 1 to jitter each delay with,
      e.g. a factor of 0.5 and a delay of 50ms will result in delays of
      50, [25; 75], [12; 112], ... or concretely 50, 70, 100, 65, ..."
  ([src delay-ms] (delay src delay-ms {}))
  ([src delay-ms opts]
   (if (> delay-ms 0)
     (let [{:keys [max-delay-ms backoff-factor] :as opts} (delay-opts delay-ms opts)
           next-delay (volatile! delay-ms)
           jitter (compute-jitter opts)
           advance #(Math/max 0 (jitter (Math/min (long (* % backoff-factor)) max-delay-ms)))]
       (for [_ src]
         (let [d @next-delay]
           (vswap! next-delay advance)
           d)))
     src)))

(defn limit-retries
  "Limits the number of retries available through seq `src` to
  `max-retries` - a non-negative integer."
  [src max-retries]
  (let [max-retries (Math/max max-retries (int 0))]
    (if (> max-retries 0)
      (take max-retries src)
      [])))

(defn limit-duration
  "The retries available through seq `src` will be available to take for
  `max-duration-ms` milliseconds - a non-negative float. The countdown starts
  once `limit-duration` is called.

  The last delay will be the minimum of the remaining time and the delay
  received from `src`. E.g. if execution takes 30 ms and we have a source of
  constant 30 ms delays with duration limited to 140 ms, then:

    execution:                                   #1  #2  #3  #4
    execution takes:                             30  30  30  30
    time remaining before running delay:          -  80  20   -
    delay:                                        -  30  20   -
    time remaining when next execution started: 110  50   0   -"
  [src max-duration-ms]
  (let [max-duration-ms (Math/max max-duration-ms 0)
        elapsed-since #(- (System/currentTimeMillis) %)]
    (if (> max-duration-ms 0)
      (let [start-ms (System/currentTimeMillis)
            this (fn this [items]
                   (lazy-seq
                     (let [remaining (- max-duration-ms (elapsed-since start-ms))]
                       (when (and (seq items) (> remaining 0))
                         (cons (min (first items) remaining)
                               (this (rest items)))))))]
        (this src))
      [])))

(def ^{:doc "The return value to be used in case the execution needs to be
            retried"}
  retry ::retry)
(def ^{:doc "The return value to be used in case the execution needs to be
            aborted (stopped from being retried)"}
  abort ::abort)

(let [e (Aborted.)]
  (defn abort!
    "Throws an exception which short-circuits the execution.

    Should only be used inside the retriable code. No more retries will be
    performed after an abort."
    ([] (throw e))
    ([v]
     (throw (if (instance? Throwable v)
              (Aborted. ^Throwable v)
              (Aborted. ^Object v))))))

(let [e (Retried.)]
  (defn retry!
    "Short-circuits the execution for the next retry.

    Should only be used inside the retriable code."
    ([] (throw e))
    ([v]
     (throw (if (instance? Throwable v)
              (Retried. ^Throwable v)
              (Retried. ^Object v))))))

(defn reset!
  "Short-circuits the execution and restarts the retry cycle with the new seq
  of `delays`."
  [delays]
  (throw (Reset. delays)))

(defn marker?
  "True if the value `v` is an abort/retry/reset marker.

  Useful in case you are catching an `Exception/ExceptionInfo/RuntimeException/Throwable/Error`
  in the retriable block and want to make sure you only act on a non-marker value, e.g.:

  ```
  (with-retries ...
    ...
    (catch Throwable t
      (when-not (marker? t)
        (do-something-on-failure)
      (throw t))))
  ```"
  [v]
  (or (identical? v abort)
      (identical? v retry)
      (instance? Reset v)
      (instance? Aborted v)
      (instance? Retried v)
      (instance? RetriesExceeded v)))

(defn- unwrap-marker [v]
  (cond (instance? Aborted v) (or (.value ^Aborted v) (.getCause ^Throwable v))
        (instance? Retried v) (or (.value ^Retried v) (.getCause ^Throwable v))
        :else v))

(def ^:private current-thread-executor
  (reify java.util.concurrent.Executor
    (execute [_ r]
      (r))))

(def ^{:dynamic true
       :doc "The result/exception which happened during the last
 execution. `:manifail/none` during the first execution of if the
 execution was reset."}
  *last-result* nil)

(def ^{:dynamic true
       :doc "The retry count of the current execution.
Zero during the first one or after a reset."}
  *retry-count* nil)

(def ^{:dynamic true
       :doc "The number of milliseconds spent on executions and retries up to
the current execution."}
  *elapsed-ms* nil)

(defn with-retries*
  "Executes the given function `f` at least once and then as long as retry
  seq `delays` isn't drained and execution is deemed retriable.

  A retriable execution is one which fails due to a (non-abort) exception or
  returns a special `retry` value.

  `delays` is a seq producing numbers of milliseconds to wait after each
  execution. Zero means no delay, a negative delay means that execution has to
  stop.

  If you want to specify an executor which should be used to run `f`, use
  `manifold.executor/with-executor`, e.g.:

  ```
  (manifold.executor/with-executor my-executor
    (with-retries* (retries 5)
      call-service))
  ```"
  [delays f]
  (let [reset-delays (fn [^Reset r] (.retryDelays r))
        cause (fn [^Throwable t] (.getCause t))
        ex (or (ex/executor) current-thread-executor)
        elapsed (volatile! 0)
        started (System/nanoTime)]
    (d/loop [retried 0, last-result ::none, delays' delays]
      (-> (d/future-with ex
            (vreset! elapsed (- (System/nanoTime) started))
            (binding [*last-result* last-result
                      *retry-count* retried
                      *elapsed-ms* (/ @elapsed 1e6)]
              (f)))
          (d/catch' identity)
          (d/chain'
            (fn [result]
              (cond (instance? Reset result) (d/recur 0 ::none (reset-delays result))
                    (instance? Aborted result) (throw result)
                    (identical? result abort) (abort!)

                    (or (instance? Throwable result)
                        (identical? result retry))
                    (-> (let [d (first delays')]
                          (when (or (nil? d) (< d 0))
                            (throw (RetriesExceeded. retried
                                     (when (instance? Throwable result)
                                       (if (instance? Retried result)
                                         (cause result)
                                         result)))))
                          (-> (d/deferred ex)
                              (d/timeout! d ::run)))
                        (d/chain' (fn [_] (d/recur (inc retried)
                                                   (unwrap-marker result)
                                                   (rest delays')))))
                    :else result)))))))

(defmacro with-retries
  "Macro wrapper over `with-retries*`.

  See docs for `with-retries*`."
  [delays & body]
  `(with-retries* ~delays (fn [] ~@body)))
