(ns example
  (:require [java-time :as j]
            [manifold.deferred :as d]
            [manifail :as f]))

(defn failing-service []
  (let [r (rand-int 20)]
    (cond
      (> r 18) {:status :abort, :result r}
      (> r 12) {:status :failure, :result r}
      (> r 8) (throw (Exception. "Failed!"))
      (> r 3) {:status :unauthorized}
      (> r 2) (throw (Exception. "Fine!"))
      :else {:status :ok, :result r})))

(defn authorize []
  (println "Authorized!"))

(defn call-with-retries []
  (let [retry-delays #(f/delay (f/retries 5) 50 {:jitter-factor 0.5})]
    (-> (f/with-retries (retry-delays)
          (println "Execution n." (inc f/*retry-count*) "after" f/*elapsed-ms* "ms...")
          (when f/*last-result*
            (println "Last result:" f/*last-result*))
          (try (let [{:keys [status result]} (failing-service)]
                 (println "Got status=" status ", result=" result)
                 (when (= status :abort)
                   (println "-- on before abort")
                   (f/abort! result))
                 (when (= status :failure)
                   (println "-- on before retry 1")
                   (f/retry! result))
                 (when (= status :unauthorized)
                   (println "-- on recoverable error")
                   (authorize)
                   (println "-- on before reset")
                   (f/reset! (retry-delays)))
                 result)
               (catch Exception e
                 (if (= (.getMessage e) "Fine!")
                   :ok
                   (throw e)))
               (catch Throwable t
                 (when-not (f/marker? t)
                   (println "-- on before retry 2"))
                 (throw t))))
        (d/chain #(println "-- on complete" %))
        (d/catch manifail.Aborted #(println "-- on after abort" (f/unwrap %)))
        (d/catch manifail.RetriesExceeded #(println "-- on retries exceeded" (f/unwrap %)))
        (d/catch #(println "-- on failure" %)))))
