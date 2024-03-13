
(ns scroll-lock.side-effects
    (:require [dom.api           :as dom]
              [fruits.css.api    :as css]
              [fruits.math.api   :as math]
              [fruits.string.api :as string]
              [scroll-lock.env   :as env]
              [scroll-lock.state :as state]))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn disable-dom-scroll!
  ; @ignore
  []
  ; To disable the DOCUMENT scroll, this function ...
  ; ... sets the HTML element 'overflow-y' property to 'hidden'
  ; ... sets the BODY element 'position' property to 'fixed'
  ; ... sets the BODY element 'width' property to '100%' (to avoid its collapsing)
  ; ... moves the BODY element (BTT) with the last scroll Y value (Y axis offset)
  ; ... marks the HTML element by a data attribute
  (let [scroll-y            (dom/get-scroll-y)
        body-top            (math/negative scroll-y)
        body-style          {:position :fixed :width :100% :top (css/px body-top)}
        document-style      {:overflow-y :hidden}
        document-attributes {:data-scroll-locked true}]
       (dom/merge-element-inline-style! (dom/get-document-element) document-style)
       (dom/merge-element-inline-style! (dom/get-body-element)     body-style)
       (dom/merge-element-attributes!   (dom/get-document-element) document-attributes)))

(defn enable-dom-scroll!
  ; @ignore
  []
  ; To enable the DOCUMENT scroll, this function ...
  ; ... removes the 'overflow-y' property from the HTML element
  ; ... removes the inline styles of the BODY element
  ; ... sets the scroll Y value the last Y axis offset of the BODY element
  ; ... removes the previously set mark from the HTML element
  ;
  ; Before enabling the DOM scroll, it must be checked whether the scroll hasn't
  ; been already enabled!
  ; If the scroll is already enabled, reenabling it might sets the scroll Y
  ; value to '0px', because the Y axis offset of the BODY element is '0px'
  ; when the scroll is NOT disabled!
  ;
  ; BUG#5516
  ; Google Chrome for mobile 90.0 (Google Android ?, Samsung S8+)
  ; The React package @dnd-kit/sortable (version ?) has components which cannot
  ; read the scroll Y value in case of the HTML element has the {overflow-y: scroll}
  ; CSS setting. Therefore, when enabling the scroll, this setting should be avoided
  ; and instead of replacing the {overflow-y: hidden} with {overflow-y: scroll}
  ; setting the 'overflow-y' property has to be removed!
  (if (env/dom-scroll-disabled?)
      (let [body-top (dom/get-body-inline-style-value "top")
            scroll-y (-> body-top string/to-integer math/positive)]

            ; remember what was the inline style values of position width overflow-y and top before tha scroll was locked to restore its original state!!!!!

           (dom/remove-element-inline-style-value! (dom/get-document-element) "overflow-y")
           (dom/remove-element-inline-style-value! (dom/get-body-element) "position")
           (dom/remove-element-inline-style-value! (dom/get-body-element) "width")
           (dom/remove-element-inline-style-value! (dom/get-body-element) "top")
           (dom/set-scroll-y! scroll-y)
           (dom/remove-element-attribute!          (dom/get-document-element) "data-scroll-locked"))))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn enable-scroll!
  ; @description
  ; If the document element scrolling previously disabled, this function makes
  ; it enabled again.
  ; Removes all previously set scroll prohibitions as well.
  ;
  ; @usage
  ; (enable-scroll!)
  []
  (reset! state/PROHIBITIONS nil)
  (enable-dom-scroll!))

(defn disable-scroll!
  ; @description
  ; Disables the scrolling on the document element.
  ;
  ; @usage
  ; (disable-scroll!)
  []
  (when-not (env/any-scroll-prohibition-added?)
            (swap! state/PROHIBITIONS assoc ::default-prohibition true)
            (disable-dom-scroll!)))

(defn add-scroll-prohibition!
  ; @description
  ; Sets a scroll prohibition for disabling the scrolling on the document element.
  ;
  ; The set prohibition can be removed by using the enable-scroll! function which
  ; removes all prohibitions or by using the remove-scroll-prohibition! function
  ; which removes a prohibition with the given ID.
  ;
  ; If at least one prohibition added, scrolling on the document element stays disabled.
  ;
  ; @param (keyword) prohibition-id
  ;
  ; @usage
  ; (add-scroll-prohibition! :my-prohibition)
  [prohibition-id]
  (if (env/any-scroll-prohibition-added?)
      (do (swap! state/PROHIBITIONS assoc prohibition-id true))
      (do (swap! state/PROHIBITIONS assoc prohibition-id true)
          (disable-dom-scroll!))))

(defn remove-scroll-prohibition!
  ; @description
  ; Removes the scroll prohibition with the given ID.
  ;
  ; Only enables scrolling on the document element if the removed prohibition
  ; was the last one and no more prohibition added.
  ;
  ; @param (keyword) prohibition-id
  ;
  ; @usage
  ; (remove-scroll-prohibition! :my-prohibition)
  [prohibition-id]
  (when (env/scroll-prohibition-added? prohibition-id)
        (swap! state/PROHIBITIONS dissoc prohibition-id)
        (if-not (env/any-scroll-prohibition-added?)
                (enable-dom-scroll!))))
