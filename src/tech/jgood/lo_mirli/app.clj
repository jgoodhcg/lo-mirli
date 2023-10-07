(ns tech.jgood.lo-mirli.app
  (:require [com.biffweb :as biff :refer [q]]
            [tech.jgood.lo-mirli.middleware :as mid]
            [tech.jgood.lo-mirli.ui :as ui]
            [tech.jgood.lo-mirli.settings :as settings]
            [tick.core :as t]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire])
  (:import [java.util UUID]
           [java.time ZoneId]))

(def about-page
  (ui/page
   {:base/title (str "About " settings/app-name)}
   [:p "This app was made with "
    [:a.link {:href "https://biffweb.com"} "Biff"] "."]))

(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(defn db-viz [{:keys [session biff/db]}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        query-result
        (->> (q db
                '{:find  [(pull ?entity [*])]
                  :where [[?entity :xt/id]]}))
        all-entities
        (->> query-result
             (map first)
             (filter #(uuid? (:xt/id %)))
             (map #(into (sorted-map) %)))
        all-attributes
        (->> all-entities
             (mapcat keys)
             distinct
             sort)
        table-rows
        (map (fn [entity]
               (map (fn [attr]
                      (get entity attr "_"))  ; Replace "N/A" with your preferred placeholder for missing values
                    all-attributes))
             all-entities)]
    (ui/page
     {}
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class  "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     ;; a table of all-entities in db
     [:table.w-full.rounded-lg.overflow-hidden.bg-white.shadow-md
      [:thead.bg-gray-100
       [:tr
        (for [attr all-attributes]
          [:th.py-2.px-4.text-left.text-gray-600.border-b
           (str attr)])]]
      [:tbody
       (for [row table-rows]
         [:tr.hover:bg-gray-50
          (for [attr-val row]
            [:td.py-2.px-4.border-b.text-gray-900
             (str attr-val)])])]]

     [:.h-6])))

(defn activity-form []
  [:div.m-2.w-full.md:w-96.space-y-8
   (biff/form
    {:hx-post   "/app/add-activity"
     :hx-swap   "outerHTML"
     :hx-select "#add-activity-form"
     :id        "add-activity-form"}

    [:div
     [:h2.text-base.font-semibold.leading-7.text-gray-900 "Add Activity"]
     [:p.mt-1.text-sm.leading-6.text-gray-600 "Add a new activity to your list."]]

    [:div.grid.grid-cols-1.gap-y-6

     ;; Activity Name
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "activity-name"} "Activity Name"]
      [:div.mt-2
       [:input.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:type "text" :name "activity-name" :autocomplete "off"}]]]

     ;; Is Sensitive?
     [:div.flex.items-center
      [:input.rounded.shadow-sm.mr-2.text-indigo-600.focus:ring-blue-500.focus:border-indigo-500
       {:type "checkbox" :name "sensitive" :autocomplete "off"}]
      [:label.text-sm.font-medium.leading-6.text-gray-900 {:for "sensitive"} "Is Sensitive?"]]

     ;; Notes
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "notes"} "Notes"]
      [:div.mt-2
       [:textarea.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:name "notes" :autocomplete "off"}]]]

     ;; Submit button
     [:div.mt-2.w-full
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.w-full
       {:type "submit"} "Add Activity"]]])])

(defn add-activity [{:keys [params session] :as ctx}]
  (biff/submit-tx ctx
                  [{:db/doc-type        :activity
                    :user/id            (:uid session)
                    :activity/name      (:activity-name params)
                    :activity/sensitive (boolean (:sensitive params))
                    :activity/notes     (:notes params)}])
  {:status  303
   :headers {"location" "/app"}})

(defn activity-log-form [activities]
  [:div.w-full.md:w-96.space-y-8
   (biff/form
    {:hx-post   "/app/log-activity"
     :hx-swap   "outerHTML"
     :hx-select "#log-activity-form"
     :id        "log-activity-form"}

    [:div
     [:h2.text-base.font-semibold.leading-7.text-gray-900 "Log Activity"]
     [:p.mt-1.text-sm.leading-6.text-gray-600 "Log the activity with your desired settings."]]

    [:div.grid.grid-cols-1.gap-y-6
     ;; Time Zone selection
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "time-zone"} "Time Zone"]
      [:div.mt-2
       [:select.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:name "time-zone" :required true :autocomplete "on"}
        (->> (java.time.ZoneId/getAvailableZoneIds)
             sort
             (map (fn [zoneId]
                    [:option {:value zoneId} zoneId])))]]]

     ;; Activities selection
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "activity-refs"} "Activities"]
      [:div.mt-2
       [:select.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:name "activity-refs" :multiple true :required true :autocomplete "off"}
        (map (fn [activity]
               [:option {:value (:xt/id activity)}
                (:activity/name activity)])
             activities)]]]

     ;; Timestamp input
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "timestamp"} "Timestamp"]
      [:div.mt-2
       [:input.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:type "datetime-local" :name "timestamp" :required true}]]]

     ;; Submit button
     [:div.mt-2.w-full
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.w-full
       {:type "submit"} "Log Activity"]]])])

(defn ensure-vector [item]
  (if (vector? item)
    item
    [item]))

(defn log-activity [{:keys [session params] :as ctx}]
  (let [id-strs       (-> params :activity-refs ensure-vector)
        tz            (-> params :time-zone)
        timestamp-str (-> params :timestamp)
        timestamp     (-> (str timestamp-str ":00Z[" tz "]") t/zoned-date-time t/inst)
        activity-ids  (mapv #(some-> % java.util.UUID/fromString) id-strs)
        user-id       (:uid session)]

    (biff/submit-tx ctx
                    [{:db/doc-type               :activity-log
                      :user/id                   user-id
                      :activity-log/timestamp    timestamp
                      :activity-log/activity-ids activity-ids}]))

  {:status  303
   :headers {"location" "/app"}})

(defn app [{:keys [session biff/db]}]
  (let [user-id              (:uid session)
        {:user/keys [email]} (xt/entity db user-id)]
    (ui/page
      {}
      [:div
       [:div.mb-4 "Signed in as " email ". "
        (biff/form
         {:action "/auth/signout"
          :class  "inline"}
         [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
          "Sign out"]) "."]
       [:div.flex.flex-col.md:flex-row.justify-center
        (activity-form)
        (let [activities (q db '{:find  (pull ?activity [*])
                                 :where [[?activity :activity/name]
                                         [?activity :user/id user-id]]
                                 :in    [user-id]} user-id)]
          (activity-log-form activities))]])))

(def plugin
  {:static {"/about/" about-page}
   :routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/db" {:get db-viz}]
            ["/add-activity" {:post add-activity}]
            ["/log-activity" {:post log-activity}]]})

(comment

  (t/now)
  (java.time.ZoneId/getAvailableZoneIds))
