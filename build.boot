(set-env!
  ; Test path can be included here as source-files are not included in JAR
  ; Just be careful to not AOT them
  :source-paths #{"src/cljs" "test/clj" "test/cljs"}
  :resource-paths #{"src/clj"}
  :dependencies '[[onetom/boot-lein-generate "0.1.3" :scope "test"]])

(require 'boot.lein)
(boot.lein/generate)