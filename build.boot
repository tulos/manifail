(def project 'tulos/manifail)
(def version "0.4.0")

(set-env! :resource-paths #{"src/clj"}
          :source-paths   #{"test" "src/java"}
          :dependencies   '[[org.clojure/clojure "1.8.0"]
                            [manifold "0.1.5"]
                            [adzerk/bootlaces "0.1.13"  :scope "test"]
                            [adzerk/boot-test "RELEASE" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "Failure handling with Manifold"
      :url         "https://github.com/tulos/manifail"
      :scm         {:url "https://github.com/tulos/manifail"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(require '[adzerk.bootlaces :as l :refer [push-release]]
         '[adzerk.boot-test :as t])
(l/bootlaces! version :dont-modify-paths? true)

(deftask build-jar []
  (comp (javac) (l/build-jar)))

(deftask release []
  (comp (build-jar) (push-release)))

(deftask test []
  (comp (javac) (t/test)))

(set! *warn-on-reflection* true)
