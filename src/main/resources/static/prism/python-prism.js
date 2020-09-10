/* PrismJS 1.21.0
https://prismjs.com/download.html#themes=prism-coy&languages=python&plugins=line-numbers+toolbar+copy-to-clipboard+download-button+match-braces */
var _self="undefined"!=typeof window?window:"undefined"!=typeof WorkerGlobalScope&&self instanceof WorkerGlobalScope?self:{},Prism=function(u){var c=/\blang(?:uage)?-([\w-]+)\b/i,n=0,M={manual:u.Prism&&u.Prism.manual,disableWorkerMessageHandler:u.Prism&&u.Prism.disableWorkerMessageHandler,util:{encode:function e(n){return n instanceof W?new W(n.type,e(n.content),n.alias):Array.isArray(n)?n.map(e):n.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/\u00a0/g," ")},type:function(e){return Object.prototype.toString.call(e).slice(8,-1)},objId:function(e){return e.__id||Object.defineProperty(e,"__id",{value:++n}),e.__id},clone:function t(e,r){var a,n;switch(r=r||{},M.util.type(e)){case"Object":if(n=M.util.objId(e),r[n])return r[n];for(var i in a={},r[n]=a,e)e.hasOwnProperty(i)&&(a[i]=t(e[i],r));return a;case"Array":return n=M.util.objId(e),r[n]?r[n]:(a=[],r[n]=a,e.forEach(function(e,n){a[n]=t(e,r)}),a);default:return e}},getLanguage:function(e){for(;e&&!c.test(e.className);)e=e.parentElement;return e?(e.className.match(c)||[,"none"])[1].toLowerCase():"none"},currentScript:function(){if("undefined"==typeof document)return null;if("currentScript"in document)return document.currentScript;try{throw new Error}catch(e){var n=(/at [^(\r\n]*\((.*):.+:.+\)$/i.exec(e.stack)||[])[1];if(n){var t=document.getElementsByTagName("script");for(var r in t)if(t[r].src==n)return t[r]}return null}},isActive:function(e,n,t){for(var r="no-"+n;e;){var a=e.classList;if(a.contains(n))return!0;if(a.contains(r))return!1;e=e.parentElement}return!!t}},languages:{extend:function(e,n){var t=M.util.clone(M.languages[e]);for(var r in n)t[r]=n[r];return t},insertBefore:function(t,e,n,r){var a=(r=r||M.languages)[t],i={};for(var l in a)if(a.hasOwnProperty(l)){if(l==e)for(var o in n)n.hasOwnProperty(o)&&(i[o]=n[o]);n.hasOwnProperty(l)||(i[l]=a[l])}var s=r[t];return r[t]=i,M.languages.DFS(M.languages,function(e,n){n===s&&e!=t&&(this[e]=i)}),i},DFS:function e(n,t,r,a){a=a||{};var i=M.util.objId;for(var l in n)if(n.hasOwnProperty(l)){t.call(n,l,n[l],r||l);var o=n[l],s=M.util.type(o);"Object"!==s||a[i(o)]?"Array"!==s||a[i(o)]||(a[i(o)]=!0,e(o,t,l,a)):(a[i(o)]=!0,e(o,t,null,a))}}},plugins:{},highlightAll:function(e,n){M.highlightAllUnder(document,e,n)},highlightAllUnder:function(e,n,t){var r={callback:t,container:e,selector:'code[class*="language-"], [class*="language-"] code, code[class*="lang-"], [class*="lang-"] code'};M.hooks.run("before-highlightall",r),r.elements=Array.prototype.slice.apply(r.container.querySelectorAll(r.selector)),M.hooks.run("before-all-elements-highlight",r);for(var a,i=0;a=r.elements[i++];)M.highlightElement(a,!0===n,r.callback)},highlightElement:function(e,n,t){var r=M.util.getLanguage(e),a=M.languages[r];e.className=e.className.replace(c,"").replace(/\s+/g," ")+" language-"+r;var i=e.parentElement;i&&"pre"===i.nodeName.toLowerCase()&&(i.className=i.className.replace(c,"").replace(/\s+/g," ")+" language-"+r);var l={element:e,language:r,grammar:a,code:e.textContent};function o(e){l.highlightedCode=e,M.hooks.run("before-insert",l),l.element.innerHTML=l.highlightedCode,M.hooks.run("after-highlight",l),M.hooks.run("complete",l),t&&t.call(l.element)}if(M.hooks.run("before-sanity-check",l),!l.code)return M.hooks.run("complete",l),void(t&&t.call(l.element));if(M.hooks.run("before-highlight",l),l.grammar)if(n&&u.Worker){var s=new Worker(M.filename);s.onmessage=function(e){o(e.data)},s.postMessage(JSON.stringify({language:l.language,code:l.code,immediateClose:!0}))}else o(M.highlight(l.code,l.grammar,l.language));else o(M.util.encode(l.code))},highlight:function(e,n,t){var r={code:e,grammar:n,language:t};return M.hooks.run("before-tokenize",r),r.tokens=M.tokenize(r.code,r.grammar),M.hooks.run("after-tokenize",r),W.stringify(M.util.encode(r.tokens),r.language)},tokenize:function(e,n){var t=n.rest;if(t){for(var r in t)n[r]=t[r];delete n.rest}var a=new i;return I(a,a.head,e),function e(n,t,r,a,i,l){for(var o in r)if(r.hasOwnProperty(o)&&r[o]){var s=r[o];s=Array.isArray(s)?s:[s];for(var u=0;u<s.length;++u){if(l&&l.cause==o+","+u)return;var c=s[u],g=c.inside,f=!!c.lookbehind,h=!!c.greedy,d=0,v=c.alias;if(h&&!c.pattern.global){var p=c.pattern.toString().match(/[imsuy]*$/)[0];c.pattern=RegExp(c.pattern.source,p+"g")}for(var m=c.pattern||c,y=a.next,k=i;y!==t.tail&&!(l&&k>=l.reach);k+=y.value.length,y=y.next){var b=y.value;if(t.length>n.length)return;if(!(b instanceof W)){var x=1;if(h&&y!=t.tail.prev){m.lastIndex=k;var w=m.exec(n);if(!w)break;var A=w.index+(f&&w[1]?w[1].length:0),P=w.index+w[0].length,S=k;for(S+=y.value.length;S<=A;)y=y.next,S+=y.value.length;if(S-=y.value.length,k=S,y.value instanceof W)continue;for(var E=y;E!==t.tail&&(S<P||"string"==typeof E.value);E=E.next)x++,S+=E.value.length;x--,b=n.slice(k,S),w.index-=k}else{m.lastIndex=0;var w=m.exec(b)}if(w){f&&(d=w[1]?w[1].length:0);var A=w.index+d,O=w[0].slice(d),P=A+O.length,L=b.slice(0,A),N=b.slice(P),j=k+b.length;l&&j>l.reach&&(l.reach=j);var C=y.prev;L&&(C=I(t,C,L),k+=L.length),z(t,C,x);var _=new W(o,g?M.tokenize(O,g):O,v,O);y=I(t,C,_),N&&I(t,y,N),1<x&&e(n,t,r,y.prev,k,{cause:o+","+u,reach:j})}}}}}}(e,a,n,a.head,0),function(e){var n=[],t=e.head.next;for(;t!==e.tail;)n.push(t.value),t=t.next;return n}(a)},hooks:{all:{},add:function(e,n){var t=M.hooks.all;t[e]=t[e]||[],t[e].push(n)},run:function(e,n){var t=M.hooks.all[e];if(t&&t.length)for(var r,a=0;r=t[a++];)r(n)}},Token:W};function W(e,n,t,r){this.type=e,this.content=n,this.alias=t,this.length=0|(r||"").length}function i(){var e={value:null,prev:null,next:null},n={value:null,prev:e,next:null};e.next=n,this.head=e,this.tail=n,this.length=0}function I(e,n,t){var r=n.next,a={value:t,prev:n,next:r};return n.next=a,r.prev=a,e.length++,a}function z(e,n,t){for(var r=n.next,a=0;a<t&&r!==e.tail;a++)r=r.next;(n.next=r).prev=n,e.length-=a}if(u.Prism=M,W.stringify=function n(e,t){if("string"==typeof e)return e;if(Array.isArray(e)){var r="";return e.forEach(function(e){r+=n(e,t)}),r}var a={type:e.type,content:n(e.content,t),tag:"span",classes:["token",e.type],attributes:{},language:t},i=e.alias;i&&(Array.isArray(i)?Array.prototype.push.apply(a.classes,i):a.classes.push(i)),M.hooks.run("wrap",a);var l="";for(var o in a.attributes)l+=" "+o+'="'+(a.attributes[o]||"").replace(/"/g,"&quot;")+'"';return"<"+a.tag+' class="'+a.classes.join(" ")+'"'+l+">"+a.content+"</"+a.tag+">"},!u.document)return u.addEventListener&&(M.disableWorkerMessageHandler||u.addEventListener("message",function(e){var n=JSON.parse(e.data),t=n.language,r=n.code,a=n.immediateClose;u.postMessage(M.highlight(r,M.languages[t],t)),a&&u.close()},!1)),M;var e=M.util.currentScript();function t(){M.manual||M.highlightAll()}if(e&&(M.filename=e.src,e.hasAttribute("data-manual")&&(M.manual=!0)),!M.manual){var r=document.readyState;"loading"===r||"interactive"===r&&e&&e.defer?document.addEventListener("DOMContentLoaded",t):window.requestAnimationFrame?window.requestAnimationFrame(t):window.setTimeout(t,16)}return M}(_self);"undefined"!=typeof module&&module.exports&&(module.exports=Prism),"undefined"!=typeof global&&(global.Prism=Prism);
Prism.languages.python={comment:{pattern:/(^|[^\\])#.*/,lookbehind:!0},"string-interpolation":{pattern:/(?:f|rf|fr)(?:("""|''')[\s\S]*?\1|("|')(?:\\.|(?!\2)[^\\\r\n])*\2)/i,greedy:!0,inside:{interpolation:{pattern:/((?:^|[^{])(?:{{)*){(?!{)(?:[^{}]|{(?!{)(?:[^{}]|{(?!{)(?:[^{}])+})+})+}/,lookbehind:!0,inside:{"format-spec":{pattern:/(:)[^:(){}]+(?=}$)/,lookbehind:!0},"conversion-option":{pattern:/![sra](?=[:}]$)/,alias:"punctuation"},rest:null}},string:/[\s\S]+/}},"triple-quoted-string":{pattern:/(?:[rub]|rb|br)?("""|''')[\s\S]*?\1/i,greedy:!0,alias:"string"},string:{pattern:/(?:[rub]|rb|br)?("|')(?:\\.|(?!\1)[^\\\r\n])*\1/i,greedy:!0},function:{pattern:/((?:^|\s)def[ \t]+)[a-zA-Z_]\w*(?=\s*\()/g,lookbehind:!0},"class-name":{pattern:/(\bclass\s+)\w+/i,lookbehind:!0},decorator:{pattern:/(^\s*)@\w+(?:\.\w+)*/im,lookbehind:!0,alias:["annotation","punctuation"],inside:{punctuation:/\./}},keyword:/\b(?:and|as|assert|async|await|break|class|continue|def|del|elif|else|except|exec|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|print|raise|return|try|while|with|yield)\b/,builtin:/\b(?:__import__|abs|all|any|apply|ascii|basestring|bin|bool|buffer|bytearray|bytes|callable|chr|classmethod|cmp|coerce|compile|complex|delattr|dict|dir|divmod|enumerate|eval|execfile|file|filter|float|format|frozenset|getattr|globals|hasattr|hash|help|hex|id|input|int|intern|isinstance|issubclass|iter|len|list|locals|long|map|max|memoryview|min|next|object|oct|open|ord|pow|property|range|raw_input|reduce|reload|repr|reversed|round|set|setattr|slice|sorted|staticmethod|str|sum|super|tuple|type|unichr|unicode|vars|xrange|zip)\b/,boolean:/\b(?:True|False|None)\b/,number:/(?:\b(?=\d)|\B(?=\.))(?:0[bo])?(?:(?:\d|0x[\da-f])[\da-f]*\.?\d*|\.\d+)(?:e[+-]?\d+)?j?\b/i,operator:/[-+%=]=?|!=|\*\*?=?|\/\/?=?|<[<=>]?|>[=>]?|[&|^~]/,punctuation:/[{}[\];(),.:]/},Prism.languages.python["string-interpolation"].inside.interpolation.inside.rest=Prism.languages.python,Prism.languages.py=Prism.languages.python;
!function(){if("undefined"!=typeof self&&self.Prism&&self.document){var o="line-numbers",a=/\n(?!$)/g,e=Prism.plugins.lineNumbers={getLine:function(e,n){if("PRE"===e.tagName&&e.classList.contains(o)){var t=e.querySelector(".line-numbers-rows"),i=parseInt(e.getAttribute("data-start"),10)||1,r=i+(t.children.length-1);n<i&&(n=i),r<n&&(n=r);var s=n-i;return t.children[s]}},resize:function(e){u([e])},assumeViewportIndependence:!0},t=function(e){return e?window.getComputedStyle?getComputedStyle(e):e.currentStyle||null:null},n=void 0;window.addEventListener("resize",function(){e.assumeViewportIndependence&&n===window.innerWidth||(n=window.innerWidth,u(Array.prototype.slice.call(document.querySelectorAll("pre."+o))))}),Prism.hooks.add("complete",function(e){if(e.code){var n=e.element,t=n.parentNode;if(t&&/pre/i.test(t.nodeName)&&!n.querySelector(".line-numbers-rows")&&Prism.util.isActive(n,o)){n.classList.remove(o),t.classList.add(o);var i,r=e.code.match(a),s=r?r.length+1:1,l=new Array(s+1).join("<span></span>");(i=document.createElement("span")).setAttribute("aria-hidden","true"),i.className="line-numbers-rows",i.innerHTML=l,t.hasAttribute("data-start")&&(t.style.counterReset="linenumber "+(parseInt(t.getAttribute("data-start"),10)-1)),e.element.appendChild(i),u([t]),Prism.hooks.run("line-numbers",e)}}}),Prism.hooks.add("line-numbers",function(e){e.plugins=e.plugins||{},e.plugins.lineNumbers=!0})}function u(e){if(0!=(e=e.filter(function(e){var n=t(e)["white-space"];return"pre-wrap"===n||"pre-line"===n})).length){var n=e.map(function(e){var n=e.querySelector("code"),t=e.querySelector(".line-numbers-rows");if(n&&t){var i=e.querySelector(".line-numbers-sizer"),r=n.textContent.split(a);i||((i=document.createElement("span")).className="line-numbers-sizer",n.appendChild(i)),i.innerHTML="0",i.style.display="block";var s=i.getBoundingClientRect().height;return i.innerHTML="",{element:e,lines:r,lineHeights:[],oneLinerHeight:s,sizer:i}}}).filter(Boolean);n.forEach(function(e){var i=e.sizer,n=e.lines,r=e.lineHeights,s=e.oneLinerHeight;r[n.length-1]=void 0,n.forEach(function(e,n){if(e&&1<e.length){var t=i.appendChild(document.createElement("span"));t.style.display="block",t.textContent=e}else r[n]=s})}),n.forEach(function(e){for(var n=e.sizer,t=e.lineHeights,i=0,r=0;r<t.length;r++)void 0===t[r]&&(t[r]=n.children[i++].getBoundingClientRect().height)}),n.forEach(function(e){var n=e.sizer,t=e.element.querySelector(".line-numbers-rows");n.style.display="none",n.innerHTML="",e.lineHeights.forEach(function(e,n){t.children[n].style.height=e+"px"})})}}}();
!function(){if("undefined"!=typeof self&&self.Prism&&self.document){var i=[],l={},c=function(){};Prism.plugins.toolbar={};var e=Prism.plugins.toolbar.registerButton=function(e,n){var t;t="function"==typeof n?n:function(e){var t;return"function"==typeof n.onClick?((t=document.createElement("button")).type="button",t.addEventListener("click",function(){n.onClick.call(this,e)})):"string"==typeof n.url?(t=document.createElement("a")).href=n.url:t=document.createElement("span"),n.className&&t.classList.add(n.className),t.textContent=n.text,t},e in l?console.warn('There is a button with the key "'+e+'" registered already.'):i.push(l[e]=t)},t=Prism.plugins.toolbar.hook=function(a){var e=a.element.parentNode;if(e&&/pre/i.test(e.nodeName)&&!e.parentNode.classList.contains("code-toolbar")){var t=document.createElement("div");t.classList.add("code-toolbar"),e.parentNode.insertBefore(t,e),t.appendChild(e);var r=document.createElement("div");r.classList.add("toolbar");var n=i,o=function(e){for(;e;){var t=e.getAttribute("data-toolbar-order");if(null!=t)return(t=t.trim()).length?t.split(/\s*,\s*/g):[];e=e.parentElement}}(a.element);o&&(n=o.map(function(e){return l[e]||c})),n.forEach(function(e){var t=e(a);if(t){var n=document.createElement("div");n.classList.add("toolbar-item"),n.appendChild(t),r.appendChild(n)}}),t.appendChild(r)}};e("label",function(e){var t=e.element.parentNode;if(t&&/pre/i.test(t.nodeName)&&t.hasAttribute("data-label")){var n,a,r=t.getAttribute("data-label");try{a=document.querySelector("template#"+r)}catch(e){}return a?n=a.content:(t.hasAttribute("data-url")?(n=document.createElement("a")).href=t.getAttribute("data-url"):n=document.createElement("span"),n.textContent=r),n}}),Prism.hooks.add("complete",t)}}();
!function(){if("undefined"!=typeof self&&self.Prism&&self.document)if(Prism.plugins.toolbar){var i=window.ClipboardJS||void 0;i||"function"!=typeof require||(i=require("clipboard"));var c=[];if(!i){var o=document.createElement("script"),t=document.querySelector("head");o.onload=function(){if(i=window.ClipboardJS)for(;c.length;)c.pop()()},o.src="https://cdnjs.cloudflare.com/ajax/libs/clipboard.js/2.0.0/clipboard.min.js",t.appendChild(o)}Prism.plugins.toolbar.registerButton("copy-to-clipboard",function(o){var t=document.createElement("button");t.textContent="Copy";var e=o.element;return i?n():c.push(n),t;function n(){var o=new i(t,{text:function(){return e.textContent}});o.on("success",function(){t.textContent="Copied!",r()}),o.on("error",function(){t.textContent="Press Ctrl+C to copy",r()})}function r(){setTimeout(function(){t.textContent="Copy"},5e3)}})}else console.warn("Copy to Clipboard plugin loaded before Toolbar plugin.")}();
"undefined"!=typeof self&&self.Prism&&self.document&&document.querySelector&&Prism.plugins.toolbar.registerButton("download-file",function(t){var e=t.element.parentNode;if(e&&/pre/i.test(e.nodeName)&&e.hasAttribute("data-src")&&e.hasAttribute("data-download-link")){var a=e.getAttribute("data-src"),n=document.createElement("a");return n.textContent=e.getAttribute("data-download-link-label")||"Download",n.setAttribute("download",""),n.href=a,n}});
!function(){if("undefined"!=typeof self&&self.Prism&&self.document){var c=/(?:^|\s)match-braces(?:\s|$)/,a=/(?:^|\s)brace-hover(?:\s|$)/,l=/(?:^|\s)brace-selected(?:\s|$)/,n=/(?:^|\s)no-brace-hover(?:\s|$)/,t=/(?:^|\s)no-brace-select(?:\s|$)/,u={"(":")","[":"]","{":"}"},f={"(":"brace-round","[":"brace-square","{":"brace-curly"},m=0,r=/^(pair-\d+-)(open|close)$/;Prism.hooks.add("complete",function(e){var a=e.element,n=a.parentElement;if(n&&"PRE"==n.tagName){for(var t=[],r=a;r;r=r.parentElement)if(c.test(r.className)){t.push("(","[","{");break}if(0!=t.length){n.__listenerAdded||(n.addEventListener("mousedown",function(){var e=n.querySelector("code");Array.prototype.slice.call(e.querySelectorAll(".brace-selected")).forEach(function(e){e.className=e.className.replace(l," ")})}),Object.defineProperty(n,"__listenerAdded",{value:!0}));var o=Array.prototype.slice.call(a.querySelectorAll("span.token.punctuation")),i=[];t.forEach(function(e){for(var a=u[e],n=f[e],t=[],r=[],s=0;s<o.length;s++){var c=o[s];if(0==c.childElementCount){var l=c.textContent;l===e?(i.push({index:s,open:!0,element:c}),c.className+=" "+n,c.className+=" brace-open",r.push(s)):l===a&&(i.push({index:s,open:!1,element:c}),c.className+=" "+n,c.className+=" brace-close",r.length&&t.push([s,r.pop()]))}}t.forEach(function(e){var a="pair-"+m+++"-",n=o[e[0]],t=o[e[1]];n.id=a+"open",t.id=a+"close",[n,t].forEach(function(e){e.addEventListener("mouseenter",p),e.addEventListener("mouseleave",d),e.addEventListener("click",h)})})});var s=0;i.sort(function(e,a){return e.index-a.index}),i.forEach(function(e){e.open?(e.element.className+=" brace-level-"+(s%12+1),s++):(s=Math.max(0,s-1),e.element.className+=" brace-level-"+(s%12+1))})}}})}function s(e){var a=r.exec(e.id);return document.querySelector("#"+a[1]+("open"==a[2]?"close":"open"))}function p(){for(var e=this.parentElement;e;e=e.parentElement)if(n.test(e.className))return;[this,s(this)].forEach(function(e){e.className=(e.className.replace(a," ")+" brace-hover").replace(/\s+/g," ")})}function d(){[this,s(this)].forEach(function(e){e.className=e.className.replace(a," ")})}function h(){for(var e=this.parentElement;e;e=e.parentElement)if(t.test(e.className))return;[this,s(this)].forEach(function(e){e.className=(e.className.replace(l," ")+" brace-selected").replace(/\s+/g," ")})}}();
