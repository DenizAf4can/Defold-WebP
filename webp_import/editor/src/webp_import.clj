(ns editor.webp-import
  (:require [dynamo.graph :as g]
            [editor.image :as image]
            [editor.properties-view :as properties-view]
            [editor.resource-dialog :as resource-dialog]
            [editor.workspace :as workspace]))

(def ^:private webp-ext "webp")
(def ^:private built-in-image-exts #{"jpg" "jpeg" "png"})
(def ^:private atlas-resource-exts #{"atlas" "tilesource" "tileset"})
(def ^:private image-icon "icons/32/Icons_25-AT-Image.png")

(defn- ext-set [ext]
  (cond
    (nil? ext) #{}
    (string? ext) #{ext}
    (coll? ext) (set ext)
    :else #{}))

(defn- ext-vec [ext]
  (cond
    (nil? ext) []
    (string? ext) [ext]
    (coll? ext) (vec ext)
    :else [ext]))

(defn- webp-compatible-filter? [ext]
  (let [exts (ext-set ext)]
    (boolean
      (or (some built-in-image-exts exts)
          (some atlas-resource-exts exts)))))

(defn- include-webp [ext]
  (if (and ext (webp-compatible-filter? ext))
    (let [exts (ext-vec ext)]
      (if (some #{webp-ext} exts)
        ext
        (conj exts webp-ext)))
    ext))

(defn- load-webp-image
  [project self _resource]
  (concat
    (g/connect project :build-settings self :build-settings)
    (g/connect project :texture-profiles self :texture-profiles)))

(defn- ensure-webp-imageio! []
  (let [webp-imageio-class (workspace/load-class! "com.defold.extension.pipeline.webp.WebPImageIO")
        install-method (.getMethod webp-imageio-class "install" (make-array Class 0))]
    (.invoke install-method nil (object-array 0))))

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
                          (make-dialog workspace project (update options :ext include-webp)))))
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
  (g/transact
    (concat
      (register-resource-types workspace)
      (workspace/register-resource-kind-extension workspace :atlas webp-ext))))

(fn [workspace]
  (load-plugin workspace))
