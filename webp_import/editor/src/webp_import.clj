(ns editor.webp-import
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [dynamo.graph :as g]
            [editor.atlas]
            [editor.handler :as handler]
            [editor.image :as image]
            [editor.properties-view :as properties-view]
            [editor.resource-dialog :as resource-dialog]
            [editor.resource :as resource]
            [editor.ui :as ui]
            [editor.workspace :as workspace]
            [editor.pipeline.texture-set-gen :as texture-set-gen]
            [editor.types :as types])
  (:import [java.awt.image BufferedImage]
           [java.io ByteArrayOutputStream File]
           [javax.imageio ImageIO]))

(def ^:private webp-ext "webp")
(def ^:private built-in-image-exts #{"jpg" "jpeg" "png"})
(def ^:private atlas-resource-exts #{"atlas" "tilesource" "tileset" "texture" "texturec" "a.texturesetc" "t.texturesetc"})
(def ^:private image-icon "icons/32/Icons_25-AT-Image.png")
(defonce ^:private decoded-cache (atom {}))
(defonce ^:private synthetic-frame-images (atom {}))

(defrecord WebPFrameResource [workspace source-resource proj-path resource-name frame-index frame-image]
  resource/Resource
  (children [_this] nil)
  (ext [_this] webp-ext)
  (resource-type [this] (resource/resource-type source-resource))
  (source-type [_this] :file)
  (exists? [_this] true)
  (read-only? [_this] true)
  (symlink? [_this] false)
  (path [_this] (subs proj-path 1))
  (abs-path [_this] (str (resource/abs-path source-resource) "#frame-" frame-index))
  (proj-path [_this] proj-path)
  (resource-name [_this] resource-name)
  (workspace [_this] workspace)
  (resource-hash [_this] (hash [proj-path frame-index (resource/resource-hash source-resource)]))
  (openable? [_this] false)
  (editable? [_this] false)
  (loaded? [_this] true)

  io/IOFactory
  (make-input-stream [_this opts]
    (let [out (ByteArrayOutputStream.)]
      (ImageIO/write ^BufferedImage frame-image "png" out)
      (io/make-input-stream (.toByteArray out) opts)))
  (make-reader [this opts] (io/make-reader (io/make-input-stream this opts) opts))
  (make-output-stream [_this _opts] (throw (Exception. "Synthetic WebP frame resources are read-only.")))
  (make-writer [this opts] (io/make-writer (io/make-output-stream this opts) opts)))

(defn- ext-set [ext]
  (cond
    (nil? ext) #{}
    (string? ext) #{ext}
    (coll? ext) (set ext)
    :else #{}))

(defn- normalize-ext
  [ext]
  (some-> ext str string/lower-case (string/replace #"^\." "")))

(defn- ext-vec [ext]
  (cond
    (nil? ext) []
    (string? ext) [ext]
    (coll? ext) (vec ext)
    :else [ext]))

(defn- webp-compatible-filter? [ext]
  (let [exts (set (map normalize-ext (ext-set ext)))]
    (boolean
      (or (some built-in-image-exts exts)
          (some atlas-resource-exts exts)))))

(defn- include-webp [ext]
  (if (and ext (webp-compatible-filter? ext))
    (let [exts (ext-vec ext)]
      (if (some #{webp-ext} (map normalize-ext exts))
        ext
        (conj exts webp-ext)))
    ext))

(defn- warn
  [message & kvs]
  (binding [*out* *err*]
    (println (str "WebP Import: " message))
    (when-let [exception (:exception (apply hash-map kvs))]
      (.printStackTrace ^Throwable exception))))

(defn- load-webp-image
  [project self _resource]
  (concat
    (g/connect project :build-settings self :build-settings)
    (g/connect project :texture-profiles self :texture-profiles)))

(defn- ensure-webp-imageio! []
  (let [webp-imageio-class (workspace/load-class! "com.defold.extension.pipeline.webp.WebPImageIO")
        install-method (.getMethod webp-imageio-class "install" (make-array Class 0))]
    (.invoke install-method nil (object-array 0))))

(defn- webp-resource?
  [resource]
  (and (resource/resource? resource)
       (= webp-ext (normalize-ext (resource/type-ext resource)))))

(defn- bytes-from-resource
  ^bytes [resource]
  (with-open [input (io/input-stream resource)
              output (ByteArrayOutputStream.)]
    (io/copy input output)
    (.toByteArray output)))

(defn- decoded-webp
  [resource]
  (when (webp-resource? resource)
    (ensure-webp-imageio!)
    (let [cache-key [(resource/proj-path resource) (resource/resource-hash resource)]]
      (if-let [decoded (@decoded-cache cache-key)]
        decoded
        (let [webp-frames-class (workspace/load-class! "com.defold.extension.pipeline.webp.WebPFrames")
              read-method (.getMethod webp-frames-class "read" (into-array Class [(Class/forName "[B")]))
              decoded (.invoke read-method nil (object-array [(bytes-from-resource resource)]))]
          (swap! decoded-cache assoc cache-key decoded)
          decoded)))))

(defn- decoded-average-fps
  ^long [decoded]
  (.intValue ^Integer (.invoke (.getMethod (.getClass decoded) "getAverageFps" (make-array Class 0)) decoded (object-array 0))))

(defn- decoded-frames
  [decoded]
  (vec (.invoke (.getMethod (.getClass decoded) "getFrames" (make-array Class 0)) decoded (object-array 0))))

(defn- frame-digits
  [frame-count]
  (max 2 (count (str frame-count))))

(defn- frame-resource-name
  [source-resource frame-index frame-count]
  (format (str "%s_%0" (frame-digits frame-count) "d.webp")
          (resource/base-name source-resource)
          frame-index))

(defn- frame-proj-path
  [source-resource frame-index frame-count]
  (format (str "%s#frame-%0" (frame-digits frame-count) "d")
          (resource/proj-path source-resource)
          frame-index))

(defn- synthetic-frame-resource
  [source-resource frame-index frame-count frame-image]
  (let [proj-path (frame-proj-path source-resource frame-index frame-count)
        frame-resource (->WebPFrameResource
                         (resource/workspace source-resource)
                         source-resource
                         proj-path
                         (frame-resource-name source-resource frame-index frame-count)
                         frame-index
                         frame-image)]
    (swap! synthetic-frame-images assoc proj-path frame-image)
    frame-resource))

(defn- expand-webp-image
  [image]
  (try
    (let [source-resource (:path image)]
      (if-let [decoded (decoded-webp source-resource)]
        (let [frames (decoded-frames decoded)
              frame-count (count frames)]
          (if (<= frame-count 1)
            [image]
            (mapv
              (fn [frame-index ^BufferedImage frame-image]
                (with-meta
                  (types/map->Image
                    {:path (synthetic-frame-resource source-resource frame-index frame-count frame-image)
                     :contents frame-image
                     :width (.getWidth frame-image)
                     :height (.getHeight frame-image)
                     :pivot-x (:pivot-x image)
                     :pivot-y (:pivot-y image)
                     :sprite-trim-mode (:sprite-trim-mode image)})
                  (meta image)))
              (range 1 (inc frame-count))
              frames)))
        [image]))
    (catch Throwable t
      (warn "Could not expand animated WebP for atlas preview." :exception t)
      [image])))

(defn- expand-animation
  [animation]
  (let [images (:images animation)
        expanded-images (mapv expand-webp-image images)
        flat-images (vec (mapcat identity expanded-images))
        single-animated-webp? (and (= 1 (count images))
                                   (< 1 (count (first expanded-images))))
        source-resource (some-> images first :path)
        decoded (when single-animated-webp? (decoded-webp source-resource))]
    (cond-> (assoc animation :images flat-images)
      (and single-animated-webp? decoded (= :playback-none (:playback animation)))
      (assoc :playback :playback-loop-forward
             :fps (int (decoded-average-fps decoded))))))

(defn- expand-atlas-args
  [args]
  (-> args
      (update :animations #(mapv expand-animation %))
      (update :all-atlas-images #(vec (mapcat expand-webp-image %)))))

(defn- wrap-atlas-texture-set! []
  (let [atlas->texture-set-data-var #'texture-set-gen/atlas->texture-set-data
        layout-atlas-pages-var #'texture-set-gen/layout-atlas-pages
        produce-texture-set-data-var (ns-resolve 'editor.atlas 'produce-texture-set-data)]
    (when-not (::webp-import-wrapped (meta atlas->texture-set-data-var))
      (alter-var-root atlas->texture-set-data-var
                      (fn [atlas->texture-set-data]
                        (fn [animations images margin inner-padding extrude-borders max-page-size]
                          (let [expanded-animations (mapv expand-animation animations)
                                expanded-images (vec (mapcat expand-webp-image images))]
                            (atlas->texture-set-data expanded-animations expanded-images margin inner-padding extrude-borders max-page-size)))))
      (alter-meta! atlas->texture-set-data-var assoc ::webp-import-wrapped true))
    (when-not (::webp-import-wrapped (meta layout-atlas-pages-var))
      (alter-var-root layout-atlas-pages-var
                      (fn [layout-atlas-pages]
                        (fn [layout-result id->image]
                          (layout-atlas-pages layout-result (merge @synthetic-frame-images id->image)))))
      (alter-meta! layout-atlas-pages-var assoc ::webp-import-wrapped true))
    (when (and produce-texture-set-data-var
               (not (::webp-import-wrapped (meta produce-texture-set-data-var))))
      (alter-var-root produce-texture-set-data-var
                      (fn [produce-texture-set-data]
                        (fn [args]
                          (produce-texture-set-data (expand-atlas-args args)))))
      (alter-meta! produce-texture-set-data-var assoc ::webp-import-wrapped true))))

(defn- include-webp-ext! []
  (alter-var-root #'image/exts
                  (fn [exts]
                    (let [exts (vec exts)]
                      (if (some #{webp-ext} exts)
                        exts
                        (conj exts webp-ext))))))

(defn- wrap-resource-dialog! []
  (let [dialog-var #'resource-dialog/make]
    (when-not (::webp-import-wrapped (meta dialog-var))
      (alter-var-root dialog-var
                      (fn [make-dialog]
                        (fn [workspace project options]
                          (make-dialog workspace project
                                       (update options :ext include-webp)))))
      (alter-meta! dialog-var assoc ::webp-import-wrapped true))))

(defn- wrap-resource-drag-drop! []
  (let [single-drag-resource-var (ns-resolve 'editor.properties-view 'single-drag-resource)]
    (when (and single-drag-resource-var
               (not (::webp-import-wrapped (meta single-drag-resource-var))))
      (alter-var-root single-drag-resource-var
                      (fn [single-drag-resource]
                        (fn [event valid-extensions workspace]
                          (single-drag-resource event (set (include-webp valid-extensions)) workspace))))
      (alter-meta! single-drag-resource-var assoc ::webp-import-wrapped true))))

(defn- selected-webp-resources
  [selection]
  (into []
        (filter webp-resource?)
        (cond
          (nil? selection) []
          (sequential? selection) selection
          :else [selection])))

(defn- project-file
  ^File [resource path]
  (io/file (workspace/project-directory (resource/workspace resource))
           (string/replace (str path) #"^/+" "")))

(defn- export-webp-frames!
  [webp-resource]
  (let [decoded (decoded-webp webp-resource)
        frames (decoded-frames decoded)
        target-dir (project-file webp-resource (str "/generated/webp_frames/" (resource/base-name webp-resource)))]
    (.mkdirs target-dir)
    (doseq [[index ^BufferedImage frame] (map-indexed vector frames)]
      (let [file (io/file target-dir (format (str "frame_%0" (frame-digits (count frames)) "d.png") (inc index)))]
        (ImageIO/write frame "png" file)))
    (warn (format "Exported %d frame(s) from %s to %s"
                  (count frames)
                  (resource/proj-path webp-resource)
                  (.getPath target-dir)))))

(handler/defhandler :webp-import.export-selected-frames :workbench
  :label "Export Selected WebP Frames"
  (active? [selection] (seq (selected-webp-resources selection)))
  (run [selection]
    (doseq [webp-resource (selected-webp-resources selection)]
      (export-webp-frames! webp-resource))))

(defn- register-menus! []
  (handler/register-menu!
    ::menus
    :editor.app-view/menubar
    [{:id :webp-import
      :label "WebP"
      :children [{:label "Export Selected WebP Frames"
                  :command :webp-import.export-selected-frames}]}])
  (try
    (ui/invalidate-menubar-item! :editor.app-view/menubar)
    (catch Throwable _ nil)))

(defn- register-resource-types [workspace]
  (workspace/register-resource-type workspace
    :ext webp-ext
    :label "WebP Image"
    :icon image-icon
    :build-ext "texturec"
    :node-type image/ImageNode
    :load-fn load-webp-image
    :stateless? true
    :view-types [:default]))

(defn- load-plugin [workspace]
  (ensure-webp-imageio!)
  (include-webp-ext!)
  (wrap-resource-dialog!)
  (wrap-resource-drag-drop!)
  (wrap-atlas-texture-set!)
  (register-menus!)
  (g/transact
    (concat
      (register-resource-types workspace)
      (workspace/register-resource-kind-extension workspace :atlas webp-ext))))

(fn [workspace]
  (load-plugin workspace))
