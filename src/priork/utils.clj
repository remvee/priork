(ns priork.utils
  (:import [java.io File StringBufferInputStream]
           [java.security MessageDigest DigestInputStream]))

(defn sha1
  "Calculate SHA1 digest for given string."
  [string]
  (let [digest (MessageDigest/getInstance "SHA1")]
    (with-open [sbin  (StringBufferInputStream. string)]
      (with-open [din (DigestInputStream. sbin digest)]
        (while (pos? (.read din))))
      (apply str (map #(format "%02x" %) (.digest digest))))))
