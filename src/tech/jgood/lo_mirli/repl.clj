(ns tech.jgood.lo-mirli.repl
  (:require [tech.jgood.lo-mirli :as main]
            [com.biffweb :as biff :refer [q]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/assoc-db @main/system))

(defn add-fixtures []
  (biff/submit-tx (get-context)
    (-> (io/resource "fixtures.edn")
        slurp
        edn/read-string)))

(comment
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, or config.edn. If you update
  ;; secrets.env, you'll need to restart the app.
  (main/refresh)

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data (in resources/fixtures.edn), you can reset the
  ;; database by running `rm -r storage/xtdb` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  ;; Query the database
  (let [{:keys [biff/db] :as ctx} (get-context)]
    (q db
       '{:find  (pull user [*])
         :where [[user :user/email]]}))

  ;; Update an existing user's email address
  (let [{:keys [biff/db] :as ctx} (get-context)
        user-id                   (biff/lookup-id db :user/email "hello@example.com")]
    (biff/submit-tx ctx
                    [{:db/doc-type :user
                      :xt/id       user-id
                      :db/op       :update
                      :user/email  "new.address@example.com"}]))

  ;; Change all :zukte-log/zukte-ids from vecs to sets
  (let [{:keys [biff/db] :as ctx} (get-context)
        zukte-logs                (q db '{:find  (pull ?zukte-log [*])
                                          :where [[?zukte-log :zukte-log/timestamp]]})]
    (->> zukte-logs
         (mapv (fn [{ids :zukte-log/zukte-ids
                    :as zukte-log}]
                (merge zukte-log
                       {:zukte-log/zukte-ids (set ids)
                        :db/doc-type         :zukte-log
                        :db/op               :update})))
         (biff/submit-tx ctx)))

  (let [{:keys [biff/db] :as ctx} (get-context)
        user-id                   #uuid "1677c7f5-232d-47a5-9df7-244b040cdcb1"
        raw-results               (q db '{:find  [(pull ?zukte-log [*]) ?zukte-id ?zukte-name]
                                          :where [[?zukte-log :zukte-log/timestamp]
                                                  [?zukte-log :user/id user-id]
                                                  [?zukte-log :zukte-log/zukte-ids ?zukte-id]
                                                  [?zukte :zukte/id ?zukte-id]
                                                  [?zukte :zukte/name ?zukte-name]]
                                          :in    [user-id]} user-id)]
    raw-results
   #_(->> raw-results
         (group-by (fn [[zukte-log _ _]] (:zukte-log/id zukte-log))) ; Group by zukte-log id
         (map (fn [[log-id grouped-tuples]]
                (let [zukte-log-map (first (first grouped-tuples))] ; Extract the zukte-log map from the first tuple
                  (assoc zukte-log-map :zukte-log/zuktes
                         (map (fn [[_ ?zukte-id ?zukte-name]] ; Construct zukte maps
                                {:zukte/id   ?zukte-id
                                 :zukte/name ?zukte-name})
                              grouped-tuples)))))
         (into [])))

  (sort (keys (get-context)))

  ;; Check the terminal for output.
  (biff/submit-job (get-context) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-context) :echo {:foo "bar"})))
