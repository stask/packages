(set-env!
  :resource-paths #{"resources"}
  :dependencies '[[adzerk/bootlaces   "0.1.9" :scope "test"]
                  [cljsjs/boot-cljsjs "0.5.0" :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[cljsjs.boot-cljsjs.packaging :refer :all])

(def codemirror-version "5.1.0")
(def +version+ (str codemirror-version "-3"))

(task-options!
  pom  {:project     'cljsjs/codemirror
        :version     +version+
        :scm         {:url "https://github.com/cljsjs/packages"}
        :description "CodeMirror is a versatile text editor implemented in JavaScript for the browser"
        :url         "https://codemirror.net/"
        :license     {"MIT" "https://github.com/codemirror/CodeMirror/blob/master/LICENSE"}})

(require '[boot.core :as c]
         '[boot.tmpdir :as tmpd]
         '[clojure.java.io :as io]
         '[clojure.string :as string]
         '[boot.util :refer [sh]]
         '[boot.tmpdir :as tmpd])

(deftask generate-deps []
  (let [tmp (c/tmp-dir!)
        new-deps-file (io/file tmp "deps.cljs")
        path->foreign-lib (fn [path]
                            (let [[_ kind dep] (re-matches #"cljsjs/codemirror/common/(.*)/(.*).inc.js"
                                                           path)]
                              {:file path
                               :requires ["cljsjs.codemirror"]
                               :provides [(str "cljsjs.codemirror." kind "." dep)]}))]
    (with-pre-wrap
      fileset
      (let [existing-deps-file (->> fileset c/input-files (c/by-name ["deps.cljs"]) first)
            existing-deps      (-> existing-deps-file tmpd/file slurp read-string)
            dep-files          (->> fileset
                                    c/input-files
                                    (c/by-re [#"^cljsjs/codemirror/common/mode/.*\.inc\.js"
                                              #"^cljsjs/codemirror/common/addon/.*\.inc\.js"]))
            deps               (map (comp path->foreign-lib tmpd/path) dep-files)
            new-deps           (update-in existing-deps [:foreign-libs] concat deps)]
        (spit new-deps-file (pr-str new-deps))
        (-> fileset (c/add-resource tmp) c/commit!)))))

(deftask package []
  (comp
    (download :url (format "https://github.com/codemirror/CodeMirror/archive/%s.zip" codemirror-version)
              :unzip true
              :checksum "6eb686a8475ed0f0eec5129256028c5b")
    (sift :move {#"^CodeMirror-([\d\.]*)/lib/codemirror\.js"  "cljsjs/codemirror/development/codemirror.inc.js"
                 #"^CodeMirror-([\d\.]*)/lib/codemirror\.css" "cljsjs/codemirror/development/codemirror.css"
                 #"^CodeMirror-([\d\.]*)/mode/(.*)/(.*).js"   "cljsjs/codemirror/common/mode/$2.js"
                 #"^CodeMirror-([\d\.]*)/addon/(.*)/(.*).js"  "cljsjs/codemirror/common/addon/$2.js"
                 #"^CodeMirror-([\d\.]*)/addon/(.*)/(.*).css"  "cljsjs/codemirror/common/addon/$2.css"})
    (minify    :in       "cljsjs/codemirror/development/codemirror.inc.js"
               :out      "cljsjs/codemirror/production/codemirror.min.inc.js")
    (minify    :in       "cljsjs/codemirror/development/codemirror.css"
               :out      "cljsjs/codemirror/production/codemirror.min.css")
    (sift :include #{#"^cljsjs"})
    (deps-cljs :name "cljsjs.codemirror")
    (sift :move {#"^cljsjs/codemirror/common/mode/(.*)\.js" "cljsjs/codemirror/common/mode/$1.inc.js"})
    (sift :move {#"^cljsjs/codemirror/common/addon/(.*)\.js" "cljsjs/codemirror/common/addon/$1.inc.js"})
    (sift :move {#"^cljsjs/codemirror/common/addon/(.*)\.css" "cljsjs/codemirror/common/addon/$1.inc.css"})
    (generate-deps)))
