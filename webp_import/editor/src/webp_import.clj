(ns editor.webp-import
  (:require [dynamo.graph :as g]
            [editor.image :as image]
            [editor.workspace :as workspace]))

(def ^:private webp-ext "webp")
(def ^:private image-icon "icons/32/Icons_25-AT-Image.png")

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
  (g/transact
    (concat
      (register-resource-types workspace)
      (workspace/register-resource-kind-extension workspace :atlas webp-ext))))

(fn [workspace]
  (load-plugin workspace))
