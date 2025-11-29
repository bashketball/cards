(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [deps-deploy.deps-deploy :as deploy]))

(def lib 'io.github.bashketball/cards)
(def github-repo "bashketball/cards")

(defn- git-version []
  (let [describe (try
                   (str/trim (b/git-process {:git-args ["describe" "--tags" "--match" "v*"]}))
                   (catch Exception _ nil))]
    (if describe
      ;; Parse "v0.1.0" or "v0.1.0-5-gabcdef"
      (let [[_ major minor _ commits] (re-find #"v(\d+)\.(\d+)\.(\d+)(?:-(\d+)-g[a-f0-9]+)?" describe)]
        (str major "." minor "." (or commits "0")))
      ;; No tags yet
      "0.1.0")))

(def version (git-version))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; Card set directories to include in the JAR
(def card-dirs ["base" "demo-set"])

(defn- copy-edn-files
  "Copy EDN files from card directories into target/classes/cards/"
  []
  (doseq [dir card-dirs]
    (let [src-dir (io/file dir)
          dest-dir (io/file class-dir "cards" dir)]
      (when (.exists src-dir)
        (.mkdirs dest-dir)
        (doseq [f (.listFiles src-dir)]
          (when (.endsWith (.getName f) ".edn")
            (io/copy f (io/file dest-dir (.getName f)))))))))

(defn clean [_]
  (b/delete {:path "target"}))

(def pom-file (str class-dir "/META-INF/maven/io.github.bashketball/cards/pom.xml"))

(defn jar [_]
  (clean nil)
  (copy-edn-files)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis (b/create-basis {})
                :src-dirs []})
  (b/jar {:class-dir class-dir
          :jar-file jar-file})
  (println "Built:" jar-file))

(defn deploy [_]
  (jar nil)
  (deploy/deploy {:installer :remote
                  :artifact jar-file
                  :pom-file pom-file
                  :repository {"github" {:url (str "https://maven.pkg.github.com/" github-repo)
                                         :username (System/getenv "GITHUB_ACTOR")
                                         :password (System/getenv "GITHUB_TOKEN")}}})
  (println "Deployed:" lib version))
