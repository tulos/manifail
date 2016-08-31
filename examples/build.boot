(def project 'tulos/manifail-examples)

(set-env! :source-paths #{"src"}
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [tulos/manifail "0.4.0"]
                          [clojure.java-time "0.2.1"]])

(require '[manifail :as f]
         '[example :as e])
