(ns mm.core
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [cljs.core.async :as async :refer [<! >!]]
            [keechma.app-state :as app-state]
            [keechma.controller :as controller]
            [keechma.ui-component :as ui]))

(enable-console-print!)

;; =======================================================================

(defn rand-digit [a b]
  (+ a (.floor js/Math (* (inc (- b a)) (.random js/Math)))))

(defn now [] (.getTime (js/Date.)))

(defn new-problem []
  {:selected-cell [(rand-digit 1 1) (rand-digit 1 1)]
   :start-time (now)
   :user-answer ""
   :correct nil})

(defn new-stats []
  {:success {:durations []
             :count 0}
   :failure {:count 0}
   :dist {}})

;; =======================================================================

(defn append-digit [problem i] 
  (case (:correct problem)
    false (assoc problem 
                 :user-answer (str i)
                 :correct nil)
    (update problem :user-answer #(str % i))))

(defn backspace [ans]
  (if (pos? (count ans))
    (let [n (count ans)]
      (.substr ans 0 (dec n)))
    ""))

(defn grade-submission [problem]
  (if (empty? (:user-answer problem))
    (assoc problem 
           :correct nil
           :end-time nil)
    (let [[i j] (:selected-cell problem)
          ans (js/parseInt (:user-answer problem))]
      (cond
        (= (* i j) ans) (assoc problem
                               :correct true
                               :end-time (now))
        :else (assoc problem
                     :correct false
                     :end-time nil)))))

(defn submission-duration [problem]
  (if (and (:start-time problem) (:end-time problem))
    (- (:end-time problem) (:start-time problem))
    nil))

(defn update-durations
  [ds d]
  (into [] (take-last 5 (conj ds d))))

(defn log-submission [stats problem]
  (let [[i j] (:selected-cell problem)]
    (case (:correct problem)
      true (if-let [dur (submission-duration problem)]
             (-> stats
                 (update-in [:success :count] inc)
                 (update-in [:success :durations] update-durations dur)
                 (update-in [:dist [i j]] inc))
             stats)
      false (update-in stats [:failure :count] inc)
      stats)))

(defrecord MainController [])
(defmethod controller/params MainController [this params]
  true)
(defmethod controller/start MainController [this params app-data]
  (controller/execute this :new-problem)
  (assoc-in app-data [:kv :stats] (new-stats)))

(defmethod controller/handler MainController [this app-db in-chan out-chan]
  (controller/dispatcher 
    app-db in-chan
    {:new-problem (fn [app-db] 
                    (swap! app-db assoc-in [:kv :problem] (new-problem)))
     :user-answer (fn [app-db x] (swap! app-db assoc-in [:kv :problem :user-answer] x))
     :append-answer-digit (fn [app-db i]
                            (swap! app-db 
                                   update-in 
                                   [:kv :problem] append-digit i))
     :backspace (fn [app-db i]
                  (swap! app-db 
                         update-in
                         [:kv :problem :user-answer] backspace))
     :submit-answer (fn [app-db]
                      (swap! app-db update-in [:kv :problem] grade-submission)
                      (controller/execute this :log))
     :log (fn [app-db]
            (println "LOGGING")
            (let [problem (get-in @app-db [:kv :problem])]
              (swap! app-db update-in [:kv :stats] log-submission problem)))
     }))


(def controllers
  {:main (->MainController)})

;; ========================================================================

(def subscriptions
  {:problem (fn [app-db] (reaction (get-in @app-db [:kv :problem])))
   :stats (fn [app-db] (reaction (get-in @app-db [:kv :stats])))
   :dist (fn [app-db] (reaction (get-in @app-db [:kv :stats :dist])))
   })


;; =======================================================================


;;
;; Main app
;;
(defn MainRenderer [ctx]
  (fn []
    [:div.main-app
     [:h1 "Military Math - Fun"]
     [(ui/component ctx :table-mul)]
     [(ui/component ctx :console)]
     [(ui/component ctx :stats)]
    ]))

(def Main
  (ui/constructor {:renderer MainRenderer
                   :component-deps [:table-mul :console :stats]
                   :subscription-deps []}))

;;
;; Multiplication table
;;
(defn cell-type
  [problem i j]
  (let [[i0 j0] (:selected-cell problem)]
    (cond
      (and (= i i0) (= j j0)) :selected
      (or (= i i0) (= j j0)) :along
      :else :none)))

(defn HeadingCell
  [problem i j]
  (let [type (cell-type problem i j)
        [i0 j0] (:selected-cell problem)
        [c x] (cond
                (and (zero? i) (zero? j)) ["-blank" "×"]
                (zero? i) ["-col" j]
                (zero? j) ["-row" i]
                :else ["" ""])]
    [:div.-heading.-cell {:class (str c " " (name type))} x]))


(defn heatmap-hsl [v]
  (let [h (* 240 (- 1.0 v))]
    (str "hsl(" h ",100%,50%)")))

(defn heatmap [v]
  (str "RGBA(13,162,164," v))

(defn textcolor [v]
  (cond
    (< v 0.2) "#555"
    (< v 0.4) "#444"
    (< v 0.6) "#333"
    (< v 0.8) "#222"
    :else "#fff"))

(defn d->intensity
  [d]
  (if (nil? d) 0 (/ (min d 4) 4)))

(defn d->background
  [d]
  (heatmap (d->intensity d)))

(defn d->textcolor
  [d]
  (textcolor (d->intensity d)))

(defn Cell
  [problem d i j]
  (let [type (cell-type problem i j)
        label (case type
                :selected "?"
                (* i j))]
    [:div.-cell
     {:class (name type)
      :style {:background (d->background d)
              :color (d->textcolor d)}}
     [:span.-label label]]))

(defn TableMulRenderer [ctx]
  (let [problem-sub (ui/subscription ctx :problem)
        dist-sub (ui/subscription ctx :dist)]
    (fn []
      (let [problem @problem-sub
            dist @dist-sub]
        [:div.table-mul 
         (for [i (range 10)]
           ^{:key i}
           [:div.-row
            (for [j (range 10)]
               (if (or (zero? i) (zero? j))
                 ^{:key j} [HeadingCell problem i j]
                 ^{:key j} [Cell problem (get dist [i j] 0) i j]))])]))))

(def TableMul
  (ui/constructor {:renderer TableMulRenderer
                   :component-deps []
                   :subscription-deps [:problem :dist]}))

;;
;; The Console
;;
(def ^:const SPACE 32)
(def ^:const ENTER 13)
(def ^:const ZERO 48)
(def ^:const NINE 57)
(def NUMBERS "0123456789")
(def DIGITS "1234567890")
(defn code->digit [code] (nth NUMBERS (- code ZERO)))

(defn KeyboardCell
  [ctx problem i]
  [:div.-cell 
   [:button {:on-mouse-down
             #(ui/send-command ctx :append-answer-digit i)} i]])

(defn KeyboardBackspace
  [ctx problem]
  [:div.-cell.-x2
   [:button {:on-mouse-down
             #(ui/send-command ctx :backspace)} "←"]])

(defn Keyboard
  [ctx problem]
  [:div.keyboard
   (when (not (:correct problem))
     [:div.-digits
      (for [digits ["123" "456" "789"]]
        ^{:key digits}
        [:div.-row
         (for [i digits]
           ^{:key i}
           [KeyboardCell ctx problem i])])
      [:div.-row
       [KeyboardCell ctx problem 0]
       [KeyboardBackspace ctx problem]]])
   [:div.-commands
    (when (not (:correct problem))
      [:div.-cell 
       [:button 
        {:on-mouse-down #(ui/send-command ctx :submit-answer)} "Okay"]])
    [:div.-cell 
     [:button 
      {:on-click #(ui/send-command ctx :new-problem)} "New problem"]]]])

(defn ConsoleRenderer
  [ctx]
  (let [problem-sub (ui/subscription ctx :problem)]
    (fn []
      (let [{selected :selected-cell
             user-answer :user-answer
             correct :correct
             :as problem} @problem-sub
            [i j] selected]
        [:div.console
         (when (and i j) 
           [:div.-question 
            [:span.-i i] 
            [:span.-op "×"]
            [:span.-j j]
            [:div.-eq 
             [:span.-eq "="]
             (let [[c x] (case correct 
                           true ["-yes" "✓"]
                           false ["-no" "✗"]
                           ["-q" "?"])]
               [:span.-status {:class c} x])]
            [:span.-ans user-answer]])
         [Keyboard ctx problem]
        ]))))

(def Console
  (ui/constructor {:renderer ConsoleRenderer
                   :component-deps []
                   :subscription-deps [:problem]}))

(defn precision [x]
  (let [n (.floor js/Math x)
        d (.round js/Math (* 10 (- x n)))]
    (str n "." d)))

(defn format-durations [ds]
  (if (empty? ds)
    ""
    (let [avg (/ (apply + ds) (count ds))
          s (/ avg 1000.0)]
      (str (precision s) " sec"))))

(defn StatusRenderer
  [ctx]
  (let [stats-sub (ui/subscription ctx :stats)]
    (fn []
      (let [stats @stats-sub]
        [:div.status
         [:div.-yes [:span (get-in stats [:success :count])]]
         [:div.-no [:span (get-in stats [:failure :count])]]
         [:div.-speed
          [:span (format-durations (get-in stats [:success :durations]))]]]))))

(def Status
  (ui/constructor {:renderer StatusRenderer
                   :component-deps []
                   :subscription-deps [:stats]}))

(def components {:main (assoc Main :topic :main)
                 :table-mul (assoc TableMul :topic :main)
                 :console (assoc Console :topic :main)
                 :stats (assoc Status :topic :main)})

;; =======================================================================

(def app {:components components
          :html-element (.getElementById js/document "app")
          :controllers controllers
          :subscriptions subscriptions
          }
  )

(def running-app (atom nil))

(defn start-app! []
  (reset! running-app (app-state/start! app)))

(defn restart-app! []
  (if-let [x @running-app]
    (app-state/stop! x start-app!)
    (start-app!)))

(restart-app!)

(defn on-js-reload [])
