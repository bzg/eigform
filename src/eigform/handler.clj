(ns eigform.handler
  (:gen-class)
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :as params]
            [ring.middleware.multipart-params :as multipart-params]
            [ring.middleware.keyword-params :as keyword-params]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [postal.core :as postal]
            [hiccup.page :as h]
            [hiccup.element :as he]
            [eigform.config :refer :all]
            [clojure.data.csv :as data-csv]
            [clojure.java.io :as io]
            [clojure.core.async :as async])
  (:import [java.io File]))

(defn home-page []
  (h/html5
   {:lang "fr"}
   [:head
    [:title "Candidature EIG"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (h/include-css
     "https://unpkg.com/template.data.gouv.fr/dist/style/main.css"
     "css/index.css"
     )]
   [:body
    [:header {:class "navbar" :role "navigation"}
     [:div {:class "navbar__container"}
      [:a {:href "https://etalab.gouv.fr" :class "navbar__home"} "Accueil"]
      [:nav
       [:ul {:class "nav__links"}
        [:li {:class "nav__item"} [:a {:href "https://etalab.gouv.fr"} "Lien 1"]]
        [:li {:class "nav__item"} [:a {:href "https://etalab.gouv.fr"} "Lien 2"]]]]]]
    [:main {:role "main"}
     [:section {:class "section-color"}
      [:div {:class "container"}
       [:h2 {:class "section-title"} "Titre de section"]
       [:p {:class "section-subtitle"} "Sous-titre de section"]
       [:div {:class "row"}
        ;; [:p "Un paragraphe pour cette section."]
        ;; [:p "Un paragraphe pour cette section."]
        ;; [:p "Un paragraphe pour cette section."]
        ]]]
     [:section {:id "form"}
      [:div {:class "container"}
       [:div {:class "form-container"}
        [:form {:action "/file" :method "post" :enctype "multipart/form-data"}
         [:div {:class "form-group"}
          [:label {:for "nom"} "Nom :"]
          [:input {:placeholder "Proust"
                   :class       "form-control" :name "nom" :size "200" :autofocus true}]]
         [:div {:class "form-group"}
          [:label {:for "prenom"} "Prénom :"]
          [:input {:placeholder "Marcel"
                   :class       "form-control" :name "prenom" :size "200"}]]
         [:div {:class "form-group"}
          [:label {:for "email"} "Adresse de courriel :"]
          [:input {:placeholder "bzg@bzg.fr"
                   :class       "form-control" :name "email" :size "200" :type "email" :required true}]]
         [:div {:class "form-group"}
          [:label "Votre CV (pdf) :"]
          [:input {:name   "file" :type "file" :size "200" :required true
                   :accept "application/pdf"}]]
         [:div {:class "form-group"}
          [:input {:type "submit" :value "Envoyer"}]]]]]]]
    [:footer {:class "footer" :role "contentinfo"}
     [:div {:class "footer__logo"}
      [:img {:src "" :alt "Logo Etalab"}]]]]))

(defn result-page [email pdf-file]
  (h/html5
   {:lang "fr"}
   [:head
    [:title "Candidature EIG"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (h/include-css
     "https://unpkg.com/template.data.gouv.fr/dist/style/main.css"
     "css/index.css")]
   [:body
    [:div {:class "container"}
     [:h1 "Merci !"]
     [:p (str "Nous avons envoyé un email à " email " pour accuser bonne réception de votre candidature avec le fichier : " pdf-file)]]]))

(defn send-mail [{:keys [email csv-file pdf-file pdf-path]}]
  (postal/send-message
   {:host "smtp.mailgun.org"
    :port 587
    :user "postmaster@eig-forever.org"
    :pass (mailgun-password)}
   {:from    "postmaster@eig-forever.org"
    :to      email
    :subject "Accusé réception de votre candidature"
    :body    [{:type    "text/plain; charset=utf-8"
               :content (str "Bonjour,

merci pour votre candidature EIG !

Votre nom de fichier (joint) : " pdf-file "

-- 
 L'équipe EIG")}
              {:type         :attachment
               :content      csv-file
               :content-type "application/csv"}
              {:type         :attachment
               :content      pdf-path
               :content-type "application/pdf"}]}))

(def email-channel (async/chan 10))

(defn start-email-loop []
  (async/go
    (loop [message (async/<! email-channel)]
      (try (send-mail message)
           (catch Throwable e)) ;; FIXME: better log
      (recur (async/<! email-channel)))))

(defn- html-response [body]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    body})

(defn to-csv-file
  "Write csv data, a vector, to a csv file."
  [csv-data file]
  (with-open [out-file (io/writer file)]
    (data-csv/write-csv out-file csv-data)))

(defroutes app-routes
  (GET "/" [] (home-page))
  (POST "/file" [nom prenom email file]
        (let [pdf-file      (:filename file)
              pdf-temp-file (:tempfile file)
              pdf-path      (str "/tmp/" pdf-file)
              csv-file      (str "/tmp/" nom "-" prenom ".csv")]
          (to-csv-file [[nom prenom email pdf-file]] csv-file)
          (io/copy pdf-temp-file (java.io.File. pdf-path))
          (async/go
            (async/>!
             email-channel {:email    email
                            :csv-file csv-file
                            :pdf-file pdf-file
                            :pdf-path pdf-path}))
          (html-response (result-page email pdf-file))))
  (route/resources "/")
  (route/not-found "404 error"))

(def app (-> app-routes
             reload/wrap-reload
             params/wrap-params
             keyword-params/wrap-keyword-params
             multipart-params/wrap-multipart-params))

(defn -main [& args]
  (do (start-email-loop)
      (http-kit/run-server #'app {:port (port) :max-body 1000000000})))
