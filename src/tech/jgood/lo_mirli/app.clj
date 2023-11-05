(ns tech.jgood.lo-mirli.app
  (:require [com.biffweb :as biff :refer [q]]
            [tech.jgood.lo-mirli.middleware :as mid]
            [tech.jgood.lo-mirli.ui :as ui]
            [tech.jgood.lo-mirli.settings :as settings]
            [clojure.string :as str]
            [tick.core :as t]
            [potpuri.core :as pot]
            [clojure.pprint :refer [pprint]]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire])
  (:import [java.util UUID]
           [java.time ZoneId]
           [java.time ZonedDateTime]
           [java.time LocalDateTime]))

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

(defn zukte-create-form []
  [:div.m-2.w-full.md:w-96.space-y-8
   (biff/form
    {:hx-post   "/app/zukte/add"
     :hx-swap   "outerHTML"
     :hx-select "#add-zukte-form"
     :id        "add-zukte-form"}

    [:div
     [:h2.text-base.font-semibold.leading-7.text-gray-900 "Add Zukte"]
     [:p.mt-1.text-sm.leading-6.text-gray-600 "Add a new zukte to your list."]]

    [:div.grid.grid-cols-1.gap-y-6

     ;; Zukte Name
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "zukte-name"} "Zukte Name"]
      [:div.mt-2
       [:input.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:type "text" :name "zukte-name" :autocomplete "off"}]]]

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
       {:type "submit"} "Add Zukte"]]])])

(defn zukte-create! [{:keys [params session] :as ctx}]
  (biff/submit-tx ctx
                  [{:db/doc-type        :zukte
                    :user/id            (:uid session)
                    :zukte/name      (:zukte-name params)
                    :zukte/sensitive (boolean (:sensitive params))
                    :zukte/notes     (:notes params)}])
  {:status  303
   :headers {"location" "/app"}})

(defn zukte-log-create-form [{:keys [zuktes time-zone]}]
  [:div.w-full.md:w-96.space-y-8
   (biff/form
    {:hx-post   "/app/zukte/log"
     :hx-swap   "outerHTML"
     :hx-select "#log-zukte-form"
     :id        "log-zukte-form"}

    [:div
     [:h2.text-base.font-semibold.leading-7.text-gray-900 "Log Zukte"]
     [:p.mt-1.text-sm.leading-6.text-gray-600 "Log the zukte with your desired settings."]]

    [:div.grid.grid-cols-1.gap-y-6
     ;; Timestamp input
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "timestamp"} "Timestamp"]
      [:div.mt-2
       [:input.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:type "datetime-local" :name "timestamp" :required true}]]]

     ;; Zuktes selection
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "zukte-refs"} "Zuktes"]
      [:div.mt-2
       [:select.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:name "zukte-refs" :multiple true :required true :autocomplete "off"}
        (map (fn [zukte]
               [:option {:value (:xt/id zukte)}
                (:zukte/name zukte)])
             zuktes)]]]

     ;; Time Zone selection
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "time-zone"} "Time Zone"]
      [:div.mt-2
       [:select.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:name "time-zone" :required true :autocomplete "on"}
        (->> (java.time.ZoneId/getAvailableZoneIds)
             sort
             (map (fn [zoneId]
                    [:option {:value    zoneId
                              :selected (= zoneId time-zone)} zoneId])))]]]
     ;; Submit button
     [:div.mt-2.w-full
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.w-full
       {:type "submit"} "Log Zukte"]]])])

(defn ensure-vector [item]
  (if (vector? item)
    item
    [item]))

(defn zukte-log-create! [{:keys [session params] :as ctx}]
  (let [id-strs        (-> params :zukte-refs ensure-vector)
        tz             (-> params :time-zone)
        timestamp-str  (-> params :timestamp)
        local-datetime (java.time.LocalDateTime/parse timestamp-str)
        zone-id        (java.time.ZoneId/of tz)
        zdt            (java.time.ZonedDateTime/of local-datetime zone-id)
        timestamp      (-> zdt (t/inst))
        zukte-ids      (->> id-strs
                            (map #(some-> % java.util.UUID/fromString))
                            set)
        user-id        (:uid session)]

    (biff/submit-tx ctx
                    [{:db/doc-type         :zukte-log
                      :user/id             user-id
                      :zukte-log/timestamp timestamp
                      :zukte-log/zukte-ids zukte-ids}
                     {:db/op          :update
                      :db/doc-type    :user
                      :xt/id          user-id
                      :user/time-zone tz}]))

  {:status  303
   :headers {"location" "/app"}})

(defn header [{:keys [email]}]
  [:div.space-x-8
   [:span email]
   [:a.link {:href "/app/zuktes"} "zuktes"]
   [:a.link {:href "/app/zukte/logs"} "zukte logs"]
   (biff/form
    {:action "/auth/signout"
     :class  "inline"}
    [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
     "Sign out"])])

(defn app [{:keys [session biff/db]}]
  (let [user-id              (:uid session)
        {:user/keys [email]} (xt/entity db user-id)]
    (ui/page
     {}
     [:div
      (header (pot/map-of email))
      [:div.flex.flex-col.md:flex-row.justify-center
       (zukte-create-form)
       (let [zuktes    (q db '{:find  (pull ?zukte [*])
                               :where [[?zukte :zukte/name]
                                       [?zukte :user/id user-id]]
                               :in    [user-id]} user-id)
             time-zone (first (first (q db '{:find  [?tz]
                                             :where [[?user :xt/id user-id]
                                                     [?user :user/time-zone ?tz]]
                                             :in    [user-id]} user-id)))]
         (zukte-log-create-form (pot/map-of zuktes time-zone)))]])))

(defn zukte-edit-form [{id :xt/id
                        sensitive :zukte/sensitive
                        :as zukte}]
  (biff/form
   {:hx-post   "/app/zukte/edit"
    :hx-swap   "outerHTML"
    :hx-select (str "#zukte-list-item-" id)
    :id        (str "zukte-list-item-" id)}

   [:div.w-full.md:w-96.ring-4.ring-blue-500.rounded.p-2
    [:input {:type "hidden" :name "id" :value (:xt/id zukte)}]

    [:div.grid.grid-cols-1.gap-y-6

     ;; Zukte Name
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "zukte-name"} "Zukte Name"]
      [:div.mt-2
       [:input.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:type "text" :name "name" :value (:zukte/name zukte)}]]]

     ;; Is Sensitive?
     [:div.flex.items-center
      [:input.rounded.shadow-sm.mr-2.text-indigo-600.focus:ring-blue-500.focus:border-indigo-500
       {:type "checkbox" :name "sensitive" :checked (:zukte/sensitive zukte)}]
      [:label.text-sm.font-medium.leading-6.text-gray-900 {:for "sensitive"} "Is Sensitive?"]]

     ;; Notes
     [:div
      [:label.block.text-sm.font-medium.leading-6.text-gray-900 {:for "notes"} "Notes"]
      [:div.mt-2
       [:textarea.rounded-md.shadow-sm.block.w-full.border-0.py-1.5.text-gray-900.focus:ring-2.focus:ring-blue-600
        {:name "notes"} (:zukte/notes zukte)]]]

     ;; Submit button
     [:div.mt-2.w-full
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.w-full
       {:type "submit"
        :script "on click setURLParameter('edit', '')"}
       "Update Zukte"]]]]))

(defn zukte-list-item [{:zukte/keys [sensitive name notes]
                        edit-id     :edit-id
                        id          :xt/id
                        :as         zukte}]
  (let [url (str "/app/zuktes?edit=" id (when sensitive "&sensitive=true"))]
    (if (= edit-id id)
      (zukte-edit-form zukte)
      [:div.hover:bg-gray-100.transition.duration-150.p-4.border-b.border-gray-200.cursor-pointer.w-full.md:w-96
       {:id          (str "zukte-list-item-" id)
        :hx-get      url
        :hx-swap     "outerHTML"
        :script      (str "on click setURLParameter('edit', '" id
                          "') then setURLParameter('sensitive', '" sensitive "')")
        :hx-trigger  "click"
        :hx-select   (str "#zukte-list-item-" id)}
       [:div.flex.justify-between
        [:h2.text-md.font-bold name]]
       (when sensitive [:span.text-red-500.mr-2 "🙈"])
       [:p.text-sm.text-gray-600 notes]])))

(defn zukte-search-component [{:keys [sensitive search]}]
  [:div.my-2
   (biff/form
    {:id         "zukte-search"
     :hx-post    "/app/zuktes"
     :hx-swap    "outerHTML"
     :hx-trigger "search"
     :hx-select  "#zuktes-list"
     :hx-target  "#zuktes-list"}
    [:div.flex.flex-col.justify-center.my-6

     [:input.form-control.w-full.md:w-96.mb-2
      (merge {:type        "search"
              :name        "search"
              :placeholder "Begin Typing To Search Zuktes..."
              :script      "on keyup setURLParameter(me.name, me.value) then htmx.trigger('#zukte-search', 'search', {})"}

             (when (not (str/blank? search))
               {:value search}))]

     [:div.flex.flex-row.justify-start.items-center
      [:label.mr-4 {:for "sensitive"} "Sensitive"]
      [:input.rounded.mr-2
       {:type         "checkbox"
        :name         "sensitive"
        :script       "on change setURLParameter(me.name, me.checked) then htmx.trigger('#zukte-search', 'search', {})"
        :autocomplete "off"
        :checked      sensitive}]
      [:label.mx-4.text-gray-500.line-through {:for "archived"} "Archived"]
      [:input.rounded.mr-2
       {:type         "checkbox"
        :name         "archived"
        :script       "on change setURLParameter(me.name, me.checked) then htmx.trigger('#zukte-search', 'search', {})"
        :autocomplete "off"
        :disabled     true
        :checked      false}]]])])

(defn zuktes-query [{:keys [db user-id]}]
  (q db '{:find  (pull ?zukte [*])
          :where [[?zukte :zukte/name]
                  [?zukte :user/id user-id]]
          :in    [user-id]} user-id))

(defn checkbox-true? [v]
  (or (= v "on") (= v "true")))

(defn search-str-xform [s]
  (some-> s str/lower-case str/trim))

(defn zuktes-page
  "Accepts GET and POST. POST is for search form as body."
  [{:keys [session biff/db params query-params]}]
  (let [user-id                        (:uid session)
        {:user/keys [email time-zone]} (xt/entity db user-id)
        zuktes                         (zuktes-query (pot/map-of db user-id))
        edit-id                        (some-> params :edit (java.util.UUID/fromString))
        sensitive                      (or (some-> params :sensitive checkbox-true?)
                                           (some-> query-params :sensitive checkbox-true?))
        search                         (or (some-> params :search search-str-xform)
                                           (some-> query-params :search search-str-xform)
                                           "")]
    (pprint (pot/map-of :zuktes-page params query-params sensitive search))
    (ui/page
     {}
     [:div
      (header (pot/map-of email))
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.w-full.md:w-96.mt-6
       "Add zukte"]
      (zukte-search-component {:sensitive sensitive :search search})
      [:div {:id "zuktes-list"}
       (->> zuktes
            (filter (fn [{:zukte/keys [name notes]
                          this-zukte-is-sensitive :zukte/sensitive
                          id          :xt/id}]
                      (let [matches-name  (str/includes? (str/lower-case name) search)
                            matches-notes (str/includes? (str/lower-case notes) search)]
                        (and (or sensitive
                                 (-> id (= edit-id))
                                 (not this-zukte-is-sensitive))
                             (or matches-name
                                 matches-notes)))))
            (map (fn [z] (zukte-list-item (-> z (assoc :edit-id edit-id))))))]])))

(defn zukte-edit! [{:keys [session params] :as ctx}]
  (let [id        (-> params :id java.util.UUID/fromString)
        name      (:name params)
        notes     (-> params :notes str)
        sensitive (-> params :sensitive boolean)]
    (biff/submit-tx ctx
                    [{:db/op           :update
                      :db/doc-type     :zukte
                      :xt/id           id
                      :zukte/name      name
                      :zukte/notes     notes
                      :zukte/sensitive sensitive}])
    {:status  303
     :headers {"location" (str "/app/zuktes" (when sensitive "?sensitive=true"))}}))

(defn zukte-logs-query [{:keys [db user-id]}]
  (let [raw-results (q db '{:find  [(pull ?zukte-log [*]) ?zukte-id ?zukte-name ?tz]
                            :where [[?zukte-log :zukte-log/timestamp]
                                    [?zukte-log :user/id user-id]
                                    [?zukte-log :zukte-log/zukte-ids ?zukte-id]
                                    [?zukte :xt/id ?zukte-id]
                                    [?zukte :zukte/name ?zukte-name]
                                    [?user :xt/id user-id]
                                    [?user :user/time-zone ?tz]]
                            :in    [user-id]} user-id)]
    (->> raw-results
         (group-by (fn [[zukte-log _ _]] (:xt/id zukte-log))) ; Group by zukte-log id
         (map (fn [[log-id grouped-tuples]]
                ;; Extract the zukte-log map from the first tuple and tz from last
                (let [zukte-log-map (-> grouped-tuples first first)
                      tz            (-> grouped-tuples first last)]
                  (assoc zukte-log-map
                         :user/time-zone tz
                         :zukte-log/zuktes
                         (->> grouped-tuples
                              (map (fn [[_ ?zukte-id ?zukte-name]] ; Construct zukte maps
                                     {:zukte/id   ?zukte-id
                                      :zukte/name ?zukte-name})))))))
         (into [])
         (sort-by :zukte-log/timestamp)
         (reverse))))

(defn zukte-log-list-item [{:zukte-log/keys [timestamp zuktes notes]
                            user-id         :user/id
                            tz              :user/time-zone
                            id              :xt/id
                            :as             zukte-log}]
  (let [url (str "/app/zukte-logs?view=" id)
        formatted-timestamp (when timestamp
                              (-> timestamp
                                  (t/instant)
                                  (t/in (t/zone tz))
                                  (->> (t/format (t/formatter "yyyy-MM-dd HH:mm:ss z")))))]
    [:div.hover:bg-gray-100.transition.duration-150.p-4.border-b.border-gray-200.cursor-pointer.w-full.md:w-96
     {:id (str "zukte-log-list-item-" id)}
     [:span formatted-timestamp]
     (when notes [:p.text-sm.text-gray-600 notes])
     [:div.flex.flex-col.mt-2
      (for [{zukte-id   :zukte/id
             zukte-name :zukte/name} zuktes]
        [:span zukte-name])]]))

(defn zukte-logs-page
  "Accepts GET and POST. POST is for search form as body."
  [{:keys [session biff/db params query-params]}]
  (let [user-id                        (:uid session)
        {:user/keys [email time-zone]} (xt/entity db user-id)
        zukte-logs                     (zukte-logs-query (pot/map-of db user-id))
        edit-id                        (some-> params :edit (java.util.UUID/fromString))
        sensitive                      (or (some-> params :sensitive checkbox-true?)
                                           (some-> query-params :sensitive checkbox-true?))
        search                         (or (some-> params :search search-str-xform)
                                           (some-> query-params :search search-str-xform)
                                           "")]
    (ui/page
     {}
     [:div
      (header (pot/map-of email))
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.w-full.md:w-96.mt-6
       "Add Zukte Log"]
      [:div {:id "zukte-logs-list"}
       (->> zukte-logs
            (map (fn [z] (zukte-log-list-item (-> z (assoc :edit-id edit-id))))))]])))

(def plugin
  {:static {"/about/" about-page}
   :routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/db" {:get db-viz}]
            ["/zuktes" {:get  zuktes-page
                        :post zuktes-page}]
            ["/zukte/logs" {:get zukte-logs-page}]
            ["/zukte/add" {:post zukte-create!}]
            ["/zukte/log" {:post zukte-log-create!}]
            ["/zukte/edit" {:post zukte-edit!}]]})

(comment

  (t/now)
  (java.time.ZoneId/getAvailableZoneIds))
