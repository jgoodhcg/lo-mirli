(ns tech.jgood.lo-mirli.schema)

(def schema
  {:user/id      :uuid
   :user         [:map {:closed true}
                  [:xt/id                           :user/id]
                  [:user/email                      :string]
                  [:user/time-zone {:optional true} :string]
                  [:user/joined-at                  inst?]
                  [:user/foo {:optional true}       :string]
                  [:user/bar {:optional true}       :string]]
   :zukte/id     :uuid
   :zukte        [:map {:closed true}
                  [:xt/id                        :zukte/id]
                  [:user/id                      :user/id]
                  [:zukte/name                   :string]
                  [:zukte/sensitive              boolean?]
                  [:zukte/notes {:optional true} :string]]
   :zukte-log/id :uuid
   :zukte-log    [:map {:closed true}
                  [:xt/id                            :zukte-log/id]
                  [:user/id                          :user/id]
                  [:zukte-log/timestamp              inst?]
                  [:zukte-log/zukte-ids              [:vector :zukte/id]]
                  [:zukte-log/notes {:optional true} :string]]})

(def plugin
  {:schema schema})
