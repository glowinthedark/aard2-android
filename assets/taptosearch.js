function getWordAtCursor(event) {
    const range = document.caretRangeFromPoint(event.clientX, event.clientY);
    if (!range || !range.startContainer || range.startContainer.nodeType !== Node.TEXT_NODE) {
        return null;
    }

    const text = range.startContainer.textContent;
    const offset = range.startOffset;

    if (offset !== undefined && offset >= 0) {
        const left = text.slice(0, offset).match(/\S*$/)?.[0] || '';
        const right = text.slice(offset).match(/^\S*/)?.[0] || '';
        const rawWord = left + right;

        const normalizedWord = rawWord.replace(/^[^\w]+|[^\w]+$/g, '').toLowerCase();
        return normalizedWord;
    }

    return null;
}

document.body.addEventListener('click', (event) => {
    if (["A", "BUTTON", "INPUT", "TEXTAREA", "SELECT"].includes(event.target.tagName)) {
        return;
    }

    const word = getWordAtCursor(event);
    if (word && word.length > 1) {
        $SLOB.onWordTapped(word);
        console.log(`✅Word clicked: ${word}`);
    } else {
        console.log('❌');
    }
});
