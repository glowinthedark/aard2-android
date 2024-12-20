(function () {
  if (!document.onclick) {
    document.onclick = function (e) {
      if (['BODY', 'A', 'IMG', 'BUTTON', 'INPUT', 'FIGURE'].indexOf( e.target.tagName) != -1 || e.target.closest('a')) {
        return;
      }
      var selection = window.getSelection();
      selection.modify('extend', 'left', 'word');
      var left = selection.toString();
      selection.modify('extend', 'right', 'word');
      selection.modify('extend', 'right', 'word');
      var right = selection.toString().split(/['"!"#$%&\'()*+,./:;<=>?@\[\]^_\`{|}~\s\t\r\n!?.,;:<>'‘’‚‛“”„‟‹›❮❯]/)[0];
      var word = left + right;
      selection.removeAllRanges();

      if (word && word.length > 1) {
        $SLOB.onWordTapped(word);
      }
    };
  }
})();

