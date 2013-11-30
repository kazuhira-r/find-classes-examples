(import '(java.io File))

(def root-package-name (first *command-line-args*))

(def class-loader (.. Thread currentThread getContextClassLoader))

(defn package-name-to-resource-name [package-name]
  (.replace package-name \. \/))

(defn classes-tree [package-name file]
  (let [branch? (fn [pn-f] (.isDirectory (second pn-f)))
        children (fn [pn-f]
                   (map (fn [f]
                          [(str (first pn-f) "." (.getName f)) f])
                        (.listFiles (second pn-f))))
        is-class-file? (fn [pn-f] (and (.isFile (second pn-f)) (.endsWith (.getName (second pn-f)) ".class")))
        to-class-name (fn [pn-f] (first pn-f))]
    (map to-class-name (filter is-class-file? (tree-seq branch? children [package-name file])))))

(defn find-classes-with-file [target-package-name file]
  (let [file-to-class
        (fn [class-name]
            (.loadClass class-loader (.replaceAll class-name "\\.class$" "")))]
    (map file-to-class
         (classes-tree target-package-name file))))

(defn find-classes-with-jar [target-package-name jar-file]
  (let [entry-name-to-class-name
        (fn [name] (.replaceAll (.replace name \/ \.) "\\.class$" ""))
        entry-to-class
        (fn [entry] (.loadClass class-loader (entry-name-to-class-name (.getName entry))))
        is-class?
        (fn [entry] (.endsWith (.getName entry) ".class"))]
    (with-open [f jar-file]
      (map entry-to-class
           (filter is-class?
                   (enumeration-seq (.entries f)))))))

(defn find-classes [target-package-name]
  (if-let [url (.getResource class-loader (package-name-to-resource-name target-package-name))]
    (case (.getProtocol url)
          "file" (find-classes-with-file target-package-name (File. (.getFile url)))
          "jar" (find-classes-with-jar target-package-name (.. url openConnection getJarFile))
          '())
    '()))

(dorun
 (for [c (find-classes root-package-name)] (do (println (.getName c)))))
